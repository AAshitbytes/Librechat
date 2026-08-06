package com.example.librechat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshRouterTest {

    private val me = "9c11"

    private fun router(memory: Int = 200) = MeshRouter(myId = me, memory = memory)

    private fun message(from: String = "7f3a", to: String = PUBLIC, ttl: Int = START_TTL) =
        Packet.message(from = from, name = "Sender", to = to, text = "hi").copy(ttl = ttl)

    @Test
    fun `a public message is shown and passed on`() {
        val action = router().handle(message())
        assertTrue(action is Action.DeliverAndRelay)
    }

    @Test
    fun `the copy that is passed on has one hop less`() {
        val action = router().handle(message(ttl = 5)) as Action.DeliverAndRelay
        assertEquals(4, action.packet.ttl)
    }

    @Test
    fun `the same message is only handled once`() {
        val router = router()
        val packet = message()
        router.handle(packet)
        assertEquals(Action.Drop, router.handle(packet))
    }

    @Test
    fun `a message addressed to me is shown`() {
        val action = router().handle(message(to = me))
        assertTrue(action is Action.DeliverAndRelay)
    }

    @Test
    fun `a message for somebody else is passed on but not shown`() {
        val action = router().handle(message(to = "aaaa"))
        assertTrue(action is Action.Relay)
    }

    @Test
    fun `a message out of hops is shown but not passed on`() {
        val action = router().handle(message(ttl = 1))
        assertTrue(action is Action.Deliver)
    }

    @Test
    fun `a message for somebody else that is out of hops is dropped`() {
        val action = router().handle(message(to = "aaaa", ttl = 1))
        assertEquals(Action.Drop, action)
    }

    @Test
    fun `our own message coming back to us is dropped`() {
        val router = router()
        val mine = message(from = me)
        router.remember(mine.id)
        assertEquals(Action.Drop, router.handle(mine))
    }

    @Test
    fun `old ids are forgotten so the list cannot grow forever`() {
        val router = router(memory = 2)
        val first = message()
        router.handle(first)
        router.handle(message())
        router.handle(message())
        assertTrue(router.handle(first) !is Action.Drop)
    }
}
