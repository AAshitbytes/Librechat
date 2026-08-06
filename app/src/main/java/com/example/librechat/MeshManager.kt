package com.example.librechat

import android.content.Context
import android.util.Log

/**
 * Puts the whole mesh together.
 *
 * The two Bluetooth halves ([BleServer] and [BleClient]) both report the packets they receive to
 * [onLine]. That method asks [MeshRouter] what should happen, then does it: showing the message,
 * passing it on to the other phones, or both.
 */
class MeshManager(context: Context, val myName: String) {

    /** A short random name for this phone, used to address private messages. */
    val myId: String = Packet.randomHex(4)

    val store = ChatStore()

    private val router = MeshRouter(myId)
    private val server = BleServer(context, ::onLine, ::onLinkUp, ::onLinkDown)
    private val client = BleClient(context, ::onLine, ::onLinkUp, ::onLinkDown)

    // Bluetooth works with hardware addresses, the app works with node ids, so we remember which
    // is which in order to update the device list when a phone goes out of range.
    private val idByAddress = mutableMapOf<String, String>()

    fun start() {
        server.start()
        client.start()
    }

    fun stop() {
        server.stop()
        client.stop()
    }

    /** Sends a message the user typed. Pass PUBLIC as [to] for the public chat. */
    fun send(to: String, text: String) {
        val packet = Packet.message(from = myId, name = myName, to = to, text = text)
        // Remember our own message so the copy that comes back through the mesh is ignored.
        router.remember(packet.id)
        store.addOutgoing(to, packet)
        sendToEveryone(packet, except = null)
    }

    /** A packet arrived over one of the Bluetooth links. */
    private fun onLine(address: String, text: String) {
        val packet = Packet.fromJson(text) ?: return

        if (packet.type == TYPE_HELLO) {
            idByAddress[address] = packet.from
            store.addPeer(packet.from, packet.name, nearby = true)
            return
        }

        if (packet.from == myId) return

        when (val action = router.handle(packet)) {
            is Action.Drop -> Unit
            is Action.Deliver -> deliver(packet)
            is Action.Relay -> sendToEveryone(action.packet, except = address)
            is Action.DeliverAndRelay -> {
                deliver(packet)
                sendToEveryone(action.packet, except = address)
            }
        }
    }

    private fun deliver(packet: Packet) {
        // Hearing from a phone is enough to list it, even if it is several hops away.
        store.addPeer(packet.from, packet.name, nearby = false)
        store.addIncoming(packet)
    }

    /** This is the flooding step: give the packet to every phone except the one it came from. */
    private fun sendToEveryone(packet: Packet, except: String?) {
        val text = packet.toJson()
        for (address in server.addresses()) {
            if (address != except) server.send(address, text)
        }
        for (address in client.addresses()) {
            if (address != except) client.send(address, text)
        }
    }

    /** A new link is ready, so introduce ourselves over it. */
    private fun onLinkUp(address: String) {
        val hello = Packet.hello(from = myId, name = myName).toJson()
        // The address belongs to one of the two halves; the other one ignores it.
        server.send(address, hello)
        client.send(address, hello)
        Log.d(TAG, "link up with $address")
    }

    /** A link is gone, so that phone is no longer a direct neighbour. */
    private fun onLinkDown(address: String) {
        val id = idByAddress.remove(address) ?: return
        store.clearNearby(id)
        Log.d(TAG, "link down with $address")
    }
}
