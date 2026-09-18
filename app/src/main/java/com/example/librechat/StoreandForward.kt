package com.example.librechat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Small store-and-forward buffer for packets.
 *
 * A packet is kept locally and retried when a new Bluetooth link appears.
 * This means a temporary missing relay does not permanently lose the message.
 *
 * There is intentionally no delivery acknowledgement here: the existing mesh protocol
 * has no ACK packet. Duplicate filtering in MeshRouter prevents a retry from looping forever.
 */
class StoreAndForward(
    private val scope: CoroutineScope,
    private val sendPacket: (Packet) -> Unit,
    private val retryEveryMs: Long = 5_000L,
    private val maxAgeMs: Long = 5 * 60_000L,
    private val maxPackets: Int = 100,
) {

    private data class Entry(
        val packet: Packet,
        val addedAt: Long,
    )

    private val pending = LinkedHashMap<String, Entry>()

    init {
        scope.launch {
            while (isActive) {
                delay(retryEveryMs)
                retry()
            }
        }
    }

    @Synchronized
    fun add(packet: Packet) {
        if (packet.ttl <= 1) return

        pending.putIfAbsent(
            packet.id,
            Entry(packet = packet, addedAt = System.currentTimeMillis())
        )

        while (pending.size > maxPackets) {
            val oldestId = pending.entries.firstOrNull()?.key ?: break
            pending.remove(oldestId)
        }
    }

    /**
     * Try the buffered packets again.
     * A packet stays buffered because the protocol has no acknowledgement packet.
     */
    @Synchronized
    fun retry() {
        val now = System.currentTimeMillis()
        val expired = pending.filterValues { now - it.addedAt > maxAgeMs }.keys.toList()
        expired.forEach { pending.remove(it) }

        pending.values.toList().forEach { entry ->
            sendPacket(entry.packet)
        }
    }

    /** Call this immediately when a Bluetooth link becomes available. */
    fun onLinkAvailable() {
        retry()
    }
}
