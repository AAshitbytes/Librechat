package com.example.librechat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log

/**
 * The central half of a phone.
 *
 * It scans for other phones advertising our service and connects to them. Once connected it turns
 * on notifications, so packets can travel in both directions: we write to the characteristic to
 * send, and the other phone notifies us to reply.
 *
 * Runs at the same time as [BleServer].
 */
@SuppressLint("MissingPermission") // MainActivity asks for the Bluetooth permissions before this starts
class BleClient(
    private val context: Context,
    private val onLine: (address: String, text: String) -> Unit,
    private val onConnected: (address: String) -> Unit,
    private val onDisconnected: (address: String) -> Unit,
) {

    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter = manager.adapter

    // Links that finished connecting and can carry packets.
    private val links = mutableMapOf<String, BluetoothGatt>()

    // Devices we are still setting up, so the same phone is not connected to twice.
    private val connecting = mutableSetOf<String>()

    fun start() {
        val scanner = adapter.bluetoothLeScanner ?: return

        // Only report phones running LibreChat, not every Bluetooth device in the room.
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, scanCallback)
        Log.d(TAG, "scanning")
    }

    fun stop() {
        adapter.bluetoothLeScanner?.stopScan(scanCallback)
        synchronized(links) {
            links.values.forEach { it.close() }
            links.clear()
            connecting.clear()
        }
    }

    fun addresses(): List<String> = synchronized(links) { links.keys.toList() }

    fun isLinked(address: String): Boolean = synchronized(links) {
        links.containsKey(address) || connecting.contains(address)
    }

    /** Sends a packet to every phone we are connected to. */
    fun send(text: String) {
        for (address in addresses()) send(address, text)
    }

    fun send(address: String, text: String) {
        val gatt = synchronized(links) { links[address] } ?: return
        val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID) ?: return
        val bytes = text.toByteArray()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            )
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            characteristic.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device: BluetoothDevice = result.device
            synchronized(links) {
                if (isLinked(device.address)) return
                connecting.add(device.address)
            }
            Log.d(TAG, "client: connecting to ${device.address}")
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "scan failed, error $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Ask for a bigger packet size first so a whole message fits in one write.
                gatt.requestMtu(517)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                synchronized(links) {
                    links.remove(address)
                    connecting.remove(address)
                }
                gatt.close()
                onDisconnected(address)
                Log.d(TAG, "client: ${address} left")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
            if (characteristic == null) {
                gatt.disconnect()
                return
            }

            gatt.setCharacteristicNotification(characteristic, true)

            // Telling the other phone to start notifying us is done by writing to this descriptor.
            val cccd = characteristic.getDescriptor(CCCD_UUID) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // Notifications are on, so the link is ready to carry packets in both directions.
            val address = gatt.device.address
            synchronized(links) {
                connecting.remove(address)
                links[address] = gatt
            }
            onConnected(address)
            Log.d(TAG, "client: ${address} joined")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onLine(gatt.device.address, String(value))
        }

        @Deprecated("Called instead of the version above on Android 12")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = characteristic.value ?: return
            onLine(gatt.device.address, String(value))
        }
    }
}
