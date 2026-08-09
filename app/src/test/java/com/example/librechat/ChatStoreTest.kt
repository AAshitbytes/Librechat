package com.example.librechat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStoreTest {

    @Test
    fun `a phone we stop hearing from is forgotten`() {
        val store = ChatStore()
        store.addPeer("7f3a", "Prem", nearby = true, at = 1_000)

        store.removeGone(before = 5_000)

        assertTrue(store.peers.value.isEmpty())
    }

    @Test
    fun `a phone we heard from recently is kept`() {
        val store = ChatStore()
        store.addPeer("7f3a", "Prem", nearby = true, at = 9_000)

        store.removeGone(before = 5_000)

        assertEquals(1, store.peers.value.size)
    }

    @Test
    fun `hearing from a phone again keeps it in the list`() {
        val store = ChatStore()
        store.addPeer("7f3a", "Prem", nearby = true, at = 1_000)
        store.addPeer("7f3a", "Prem", nearby = true, at = 9_000)

        store.removeGone(before = 5_000)

        assertEquals(1, store.peers.value.size)
    }

    @Test
    fun `a phone is only listed once however often we hear from it`() {
        val store = ChatStore()
        store.addPeer("7f3a", "Prem", nearby = true)
        store.addPeer("7f3a", "Prem", nearby = false)

        assertEquals(1, store.peers.value.size)
    }

    @Test
    fun `losing the direct link leaves the phone listed as further away`() {
        val store = ChatStore()
        store.addPeer("7f3a", "Prem", nearby = true)

        store.clearNearby("7f3a")

        assertFalse(store.peers.value.single().nearby)
    }

    @Test
    fun `a private message goes into the chat with the phone that sent it`() {
        val store = ChatStore()
        store.addIncoming(Packet.message(from = "7f3a", name = "Prem", to = "9c11", text = "hi"))

        assertEquals(1, store.messages("7f3a").value.size)
        assertTrue(store.messages(PUBLIC).value.isEmpty())
    }

    @Test
    fun `a public message goes into the public chat`() {
        val store = ChatStore()
        store.addIncoming(Packet.message(from = "7f3a", name = "Prem", to = PUBLIC, text = "hi"))

        assertEquals(1, store.messages(PUBLIC).value.size)
    }
}
