package com.snoop.core

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import com.snoop.core.store.SnoopStore
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Central, DI-agnostic entry point that the capture adapters (Ktor, analytics) write to and the UI
 * reads from. Holds the single shared [SnoopStore]. Adapters never reference the UI or each other —
 * they only talk to this bridge, keeping capture, storage and UI fully decoupled.
 */
@OptIn(ExperimentalAtomicApi::class)
object SnoopCore {

    private val idCounter = AtomicLong(0)

    private var _store = SnoopStore()
    val store: SnoopStore get() = _store

    /** Master switch; when false, adapters drop everything (used by no-op / release gating). */
    var enabled: Boolean = true

    /** Reconfigure the ring buffer size. Call before traffic starts; clears existing entries. */
    fun configure(capacity: Int) {
        _store = SnoopStore(capacity)
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

    /**
     * Link analytics events logged shortly before a request completed to that request, so the event
     * detail can show "sent over POST /collect". Heuristic: any un-linked event within [within] of
     * [callId]'s completion is attributed to it.
     */
    fun correlateRecentAnalytics(callId: String, within: Duration = DEFAULT_CORRELATION_WINDOW) {
        if (!enabled) return
        val cutoff = now() - within
        store.mutate { list ->
            val linked = mutableListOf<String>()
            val withEvents = list.map { entry ->
                if (entry is AnalyticsEntry && entry.correlatedCallId == null && entry.timestamp >= cutoff) {
                    linked += entry.id
                    entry.copy(correlatedCallId = callId)
                } else {
                    entry
                }
            }
            if (linked.isEmpty()) return@mutate list
            withEvents.map { entry ->
                if (entry is NetworkTransaction && entry.id == callId) {
                    entry.copy(correlatedEventIds = entry.correlatedEventIds + linked)
                } else {
                    entry
                }
            }
        }
    }

    /** Test/utility hook to reset all state. */
    fun reset() {
        idCounter.store(0)
        store.clear()
        enabled = true
    }

    val entriesSnapshot: List<SnoopEntry> get() = store.entries.value

    private val DEFAULT_CORRELATION_WINDOW: Duration = 2.seconds
}
