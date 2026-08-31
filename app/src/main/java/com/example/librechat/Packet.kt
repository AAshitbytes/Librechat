package com.example.librechat

import org.json.JSONException
import org.json.JSONObject
import kotlin.random.Random

const val TYPE_HELLO = "hello"
const val TYPE_MSG = "msg"
const val TYPE_SOS = "sos"
const val PUBLIC = ""
const val START_TTL = 5

data class Packet(
    val type: String,
    val from: String,
    val name: String,
    val id: String = "",
    val to: String = PUBLIC,
    val text: String = "",
    val ttl: Int = START_TTL,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("type", type)
        json.put("from", from)
        json.put("name", name)
        if (id.isNotEmpty()) json.put("id", id)
        if (to.isNotEmpty()) json.put("to", to)
        if (text.isNotEmpty()) json.put("text", text)
        if (ttl != START_TTL) json.put("ttl", ttl)
        json.put("timestamp", timestamp)
        return json.toString()
    }

    companion object {
        fun makeHello(myId: String, myName: String): Packet =
            Packet(type = TYPE_HELLO, from = myId, name = myName)

        fun makeMsg(myId: String, myName: String, to: String, text: String, isSos: Boolean = false): Packet {
            val randomId = Random.nextInt(0, 0xFFFF).toString(16).padStart(4, '0')
            return Packet(
                type = if (isSos) TYPE_SOS else TYPE_MSG,
                from = myId,
                name = myName,
                id = randomId,
                to = to,
                text = text,
                timestamp = System.currentTimeMillis()
            )
        }

        fun fromJson(raw: String): Packet? {
            return try {
                val json = JSONObject(raw)
                Packet(
                    type = json.getString("type"),
                    from = json.getString("from"),
                    name = json.getString("name"),
                    id = json.optString("id", ""),
                    to = json.optString("to", PUBLIC),
                    text = json.optString("text", ""),
                    ttl = json.optInt("ttl", START_TTL),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
            } catch (e: JSONException) {
                null
            }
        }
    }
}