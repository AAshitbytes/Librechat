package com.example.librechat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log

/**
 * The peripheral half of a phone.
 *
 * It advertises so other phones can find this one, and it runs a GATT server holding a single
 * characteristic. A phone that connects to us writes packets into that characteristic, and we
 * send packets back to it as notifications on the same characteristic.
 *
 * Every phone runs this at the same time as [BleClient]. Being both at once is what lets phones
 * form a mesh instead of a plain client and server setup.
 */
@SuppressLint("MissingPermission") // MainActivity asks for the Bluetooth permissions before this starts
class BleServer(
    private val context: Context,
    private val onLine: (address: String, text: String) -> Unit,
    private val onConnected: (address: String) -> Unit,
    private val onDisconnected: (address: String) -> Unit,
) {

    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter = manager.adapter

    private var server: BluetoothGattServer? = null
    private var characteristic: BluetoothGattCharacteristic? = null

    // Phones that have switched notifications on, so we know who we can send to.
    private val subscribers = mutableMapOf<String, BluetoothDevice>()

    fun start() {
        openServer()
        startAdvertising()
    }

    fun stop() {
        adapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        server?.close()
        server = null
        subscribers.clear()
    }

    fun addresses(): List<String> = synchronized(subscribers) { subscribers.keys.toList() }

    /** Sends a packet to every phone connected to us. */
    fun send(text: String) {
        for (address in addresses()) send(address, text)
    }

    fun send(address: String, text: String) {
        val device = synchronized(subscribers) { subscribers[address] } ?: return
        val target = characteristic ?: return
        val server = this.server ?: return
        val bytes = text.toByteArray()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(device, target, false, bytes)
        } else {
            @Suppress("DEPRECATION")
            target.value = bytes
            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(device, target, false)
        }
    }

    private fun openServer() {
        val server = manager.openGattServer(context, callback) ?: return
        this.server = server

        val characteristic = BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        // Switching notifications on is done by writing to this extra descriptor.
        characteristic.addDescriptor(
            BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            )
        )

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(characteristic)
        server.addService(service)

        this.characteristic = characteristic
    }

    private fun startAdvertising() {
        val advertiser = adapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        // The advertisement is only 31 bytes, so it carries the service id and nothing else.
        // Names are exchanged over the connection instead, in a hello packet.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "advertising")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "advertising failed, error $errorCode")
        }
    }

    private val callback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                synchronized(subscribers) { subscribers.remove(device.address) }
                onDisconnected(device.address)
                Log.d(TAG, "server: ${device.address} left")
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                // The other phone is ready to receive, so the link is usable from now on.
                synchronized(subscribers) { subscribers[device.address] = device }
                onConnected(device.address)
                Log.d(TAG, "server: ${device.address} joined")
            }
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (characteristic.uuid == CHAR_UUID) {
                onLine(device.address, String(value))
            }
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }
    }
}
