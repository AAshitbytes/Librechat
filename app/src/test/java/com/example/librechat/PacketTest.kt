package com.example.librechat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PacketTest {

    @Test
    fun `message survives a json round trip`() {
        val sent = Packet.message(from = "7f3a", name = "Prem", to = "9c11", text = "hi")
        val received = Packet.fromJson(sent.toJson())
        assertEquals(sent, received)
    }

    @Test
    fun `hello has no text and no recipient`() {
        val hello = Packet.hello(from = "7f3a", name = "Prem")
        assertEquals(TYPE_HELLO, hello.type)
        assertEquals(PUBLIC, hello.to)
        assertEquals("", hello.text)
    }

    @Test
    fun `new messages start with a full ttl`() {
        val message = Packet.message("7f3a", "Prem", PUBLIC, "one")
        assertEquals(START_TTL, message.ttl)
    }

    @Test
    fun `each message gets its own id`() {
        val first = Packet.message("7f3a", "Prem", PUBLIC, "one")
        val second = Packet.message("7f3a", "Prem", PUBLIC, "two")
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `text that is not json decodes to null`() {
        assertNull(Packet.fromJson("not json"))
    }
}
