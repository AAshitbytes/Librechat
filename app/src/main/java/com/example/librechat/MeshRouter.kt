package com.example.librechat

/** What the app should do with a packet that just arrived. */
sealed class Action {

    /** Already seen, or nobody needs it. Throw it away. */
    data object Drop : Action()

    /** Show it to the user, but it has run out of hops so it is not passed on. */
    data class Deliver(val packet: Packet) : Action()

    /** Not for us, but other phones may still be waiting for it. */
    data class Relay(val packet: Packet) : Action()

    /** Show it to the user and pass it on as well. */
    data class DeliverAndRelay(val packet: Packet) : Action()
}

/**
 * Decides what happens to every message packet in the mesh.
 *
 * The rule is simple flooding: send everything you receive to everybody you are connected to.
 * On its own that would loop forever, so two things hold it back.
 *
 *  1. Every message carries a random id. The ids that were handled recently are remembered, and a
 *     message with an id we have already seen is thrown away instead of being passed on again.
 *  2. Every message carries a ttl, the number of hops it is still allowed to travel. Each phone
 *     takes one off before passing it on, and at zero the message stops.
 *
 * There is no Android code in this class, which is what makes the mesh rules easy to unit test.
 */
class MeshRouter(private val myId: String, private val memory: Int = 200) {

    // A map used as a fixed size queue: adding the 201st id removes the oldest one.
    private val seen = object : LinkedHashMap<String, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean {
            return size > memory
        }
    }

    /** Called when we send a message ourselves, so a copy coming back to us is recognised. */
    fun remember(id: String) {
        seen[id] = true
    }

    fun handle(packet: Packet): Action {
        if (seen.containsKey(packet.id)) return Action.Drop
        remember(packet.id)

        val forUs = packet.to == PUBLIC || packet.to == myId
        val hasHopsLeft = packet.ttl > 1
        val nextHop = packet.copy(ttl = packet.ttl - 1)

        return when {
            forUs && hasHopsLeft -> Action.DeliverAndRelay(nextHop)
            forUs -> Action.Deliver(packet)
            hasHopsLeft -> Action.Relay(nextHop)
            else -> Action.Drop
        }
    }
}
