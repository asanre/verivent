package com.snoop.core

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import com.snoop.core.store.SnoopStore
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Central, DI-agnostic entry point that the capture adapters (Ktor, analytics) write to and the UI
 * reads from. Holds the single shared [SnoopStore]. Adapters never reference the UI or each other —
 * they only talk to this bridge, keeping capture, storage and UI fully decoupled.
 */
@OptIn(ExperimentalAtomicApi::class)
object SnoopCore {

    private val idCounter = AtomicLong(0)

    val store: SnoopStore = SnoopStore()

    /**
     * Master switch; when false, adapters drop everything (used by no-op / release gating).
     * Volatile because it is flipped from wherever the host happens to be and read on every capture
     * from the Ktor and analytics threads, which share no happens-before with the writer.
     */
    @Volatile
    var enabled: Boolean = true

    /**
     * Reconfigure the store limits. Changing them clears existing entries — set them before traffic
     * starts. The store and its StateFlow survive, so already-attached collectors (Compose UI, SSE)
     * keep working.
     */
    fun configure(
        capacity: Int = store.capacity,
        maxTotalBytes: Long = store.maxTotalBytes,
    ) {
        if (capacity != store.capacity || maxTotalBytes != store.maxTotalBytes) {
            store.reconfigure(capacity = capacity, maxTotalBytes = maxTotalBytes)
        }
    }

    fun nextId(): String = idCounter.incrementAndFetch().toString()

    fun now(): Instant = Clock.System.now()

    // --- network ---

    fun recordNetwork(transaction: NetworkTransaction) {
        if (enabled) store.record(transaction)
    }

    fun updateNetwork(id: String, transform: (NetworkTransaction) -> NetworkTransaction) {
        if (!enabled) return
        store.update(id) { if (it is NetworkTransaction) transform(it) else it }
    }

    // --- analytics ---

    fun recordAnalytics(entry: AnalyticsEntry) {
        if (enabled) store.record(entry)
    }

    /** Test/utility hook to reset all state. */
    fun reset() {
        idCounter.store(0)
        store.clear()
        enabled = true
    }

    val entriesSnapshot: List<SnoopEntry> get() = store.entries.value
}
