package com.example.librechat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One line in a chat. [mine] is true for messages this phone sent, so they can be shown differently. */
data class ChatMessage(
    val fromId: String,
    val fromName: String,
    val text: String,
    val mine: Boolean,
)

/**
 * Another phone we know about.
 *
 * [nearby] is true when we have a direct Bluetooth link to it. A peer that is not nearby was
 * discovered because one of its messages reached us through another phone.
 */
data class Peer(
    val id: String,
    val name: String,
    val nearby: Boolean,
)

/**
 * Holds everything the screens display. Nothing is written to disk, so chats start empty every
 * time the app is opened.
 *
 * The values are StateFlows because Compose can watch them and redraw a screen by itself whenever
 * a message or a device arrives.
 */
class ChatStore {

    private val peerList = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = peerList

    // One conversation per chat: PUBLIC for the public chat, otherwise the other phone's id.
    private val conversations = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()

    fun messages(chatId: String): StateFlow<List<ChatMessage>> = conversation(chatId)

    @Synchronized
    private fun conversation(chatId: String): MutableStateFlow<List<ChatMessage>> {
        return conversations.getOrPut(chatId) { MutableStateFlow(emptyList()) }
    }

    @Synchronized
    fun addPeer(id: String, name: String, nearby: Boolean) {
        val known = peerList.value.find { it.id == id }
        // A phone we can already reach directly stays marked as nearby even if we later hear it
        // through somebody else as well.
        val stillNearby = nearby || (known?.nearby == true)
        val updated = peerList.value.filter { it.id != id } + Peer(id, name, stillNearby)
        peerList.value = updated.sortedWith(compareByDescending<Peer> { it.nearby }.thenBy { it.name })
    }

    /** The direct link to this phone is gone, but we may still reach it through the mesh. */
    @Synchronized
    fun clearNearby(id: String) {
        peerList.value = peerList.value.map { if (it.id == id) it.copy(nearby = false) else it }
    }

    fun addIncoming(packet: Packet) {
        val chatId = if (packet.to == PUBLIC) PUBLIC else packet.from
        add(chatId, ChatMessage(packet.from, packet.name, packet.text, mine = false))
    }

    fun addOutgoing(chatId: String, packet: Packet) {
        add(chatId, ChatMessage(packet.from, packet.name, packet.text, mine = true))
    }

    @Synchronized
    private fun add(chatId: String, message: ChatMessage) {
        val flow = conversation(chatId)
        flow.value = flow.value + message
    }

    fun nameOf(id: String): String = peerList.value.find { it.id == id }?.name ?: id
}
