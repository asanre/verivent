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

    val store: SnoopStore = SnoopStore()

    /** Master switch; when false, adapters drop everything (used by no-op / release gating). */
    var enabled: Boolean = true

    /**
     * Reconfigure the store limits. Call before traffic starts; clears existing entries. The store
     * and its StateFlow survive, so already-attached collectors (Compose UI, SSE) keep working.
     */
    fun configure(capacity: Int = store.capacity, maxTotalBytes: Long = store.maxTotalBytes) {
        store.reconfigure(capacity = capacity, maxTotalBytes = maxTotalBytes)
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
        // Built inside mutate: MutableStateFlow.update retries the lambda on contention, so the set
        // must come from this call's `list`, not a value hoisted outside. `toSet()` keeps timeline
        // order (LinkedHashSet), which the "sent over" list and its test depend on.
        store.mutate { list ->
            val linkedIds = list
                .filterIsInstance<AnalyticsEntry>()
                .filter { it.correlatedCallId == null && it.timestamp >= cutoff }
                .map { it.id }
                .toSet()
            if (linkedIds.isEmpty()) return@mutate list
            list.map { entry ->
                when {
                    entry is AnalyticsEntry && entry.id in linkedIds -> entry.copy(correlatedCallId = callId)
                    entry is NetworkTransaction && entry.id == callId ->
                        entry.copy(correlatedEventIds = entry.correlatedEventIds + linkedIds)
                    else -> entry
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
