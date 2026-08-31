package com.example.librechat

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(
    val fromId: String,
    val fromName: String,
    val text: String,
    val mine: Boolean,
    val isEmergency: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class Peer(
    val id: String,
    val name: String,
    val nearby: Boolean,
    val lastSeen: Long,
    val rssi: Int = -65
)

class ChatStore(private val context: Context? = null) {

    private val peerList = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = peerList

    private val unreadIds = MutableStateFlow<Set<String>>(emptySet())
    val unreadChatIds: StateFlow<Set<String>> = unreadIds

    private val conversations = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()

    fun messages(chatId: String): StateFlow<List<ChatMessage>> {
        return conversations.getOrPut(chatId) {
            MutableStateFlow(loadFromDisk(chatId))
        }
    }

    fun addMessage(chatId: String, message: ChatMessage) {
        val decryptedText = Security.decrypt(message.text)
        val finalMessage = message.copy(text = decryptedText)
        val flow = conversations.getOrPut(chatId) { MutableStateFlow(emptyList()) }
        val updated = flow.value + finalMessage
        flow.value = updated
        saveToDisk(chatId, updated)
    }

    fun setPeers(list: List<Peer>) {
        peerList.value = list
    }

    fun markRead(chatId: String) {
        unreadIds.value = unreadIds.value - chatId
    }

    fun markUnread(chatId: String) {
        unreadIds.value = unreadIds.value + chatId
    }

    private fun saveToDisk(chatId: String, list: List<ChatMessage>) {
        if (context == null) return
        val prefs = context.getSharedPreferences("librechat_history", Context.MODE_PRIVATE)
        val array = JSONArray()
        list.takeLast(100).forEach { msg ->
            val obj = JSONObject()
            obj.put("fromId", msg.fromId)
            obj.put("fromName", msg.fromName)
            obj.put("text", msg.text)
            obj.put("mine", msg.mine)
            obj.put("isEmergency", msg.isEmergency)
            obj.put("timestamp", msg.timestamp)
            array.put(obj)
        }
        prefs.edit().putString("chat_$chatId", array.toString()).apply()
    }

    private fun loadFromDisk(chatId: String): List<ChatMessage> {
        if (context == null) return emptyList()
        val prefs = context.getSharedPreferences("librechat_history", Context.MODE_PRIVATE)
        val raw = prefs.getString("chat_$chatId", null) ?: return emptyList()
        val result = mutableListOf<ChatMessage>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    ChatMessage(
                        fromId = obj.getString("fromId"),
                        fromName = obj.getString("fromName"),
                        text = obj.getString("text"),
                        mine = obj.getBoolean("mine"),
                        isEmergency = obj.optBoolean("isEmergency", false),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}