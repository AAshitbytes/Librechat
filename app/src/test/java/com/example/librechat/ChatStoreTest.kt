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

    @Test
    fun `peers are split into paired and discovered`() {
        val store = ChatStore()
        
        // Add two peers
        store.addPeer("id1", "Alice", nearby = true)
        store.addPeer("id2", "Bob", nearby = true)
        
        // Initially both are discovered
        assertEquals(2, store.discoveredPeers.value.size)
        assertEquals(0, store.pairedPeers.value.size)
        
        // Accept Alice
        store.updateStatus("id1", ChatRequestStatus.ACCEPTED)
        
        // Now Alice is paired, Bob is discovered
        assertEquals(1, store.pairedPeers.value.size)
        assertEquals("Alice", store.pairedPeers.value[0].name)
        assertEquals(1, store.discoveredPeers.value.size)
        assertEquals("Bob", store.discoveredPeers.value[0].name)
    }

    @Test
    fun `paired peers stay in list when offline`() {
        val store = ChatStore()
        store.addPeer("id1", "Alice", nearby = true, at = 1000)
        store.updateStatus("id1", ChatRequestStatus.ACCEPTED)
        
        // Alice goes offline
        store.removeGone(before = 5000)
        
        // Alice should still be in paired list but marked offline (lastSeen = 0)
        assertEquals(1, store.pairedPeers.value.size)
        assertEquals("Alice", store.pairedPeers.value[0].name)
        assertEquals(0L, store.pairedPeers.value[0].lastSeen)
        
        // Discovered list should be empty
        assertEquals(0, store.discoveredPeers.value.size)
    }
}
