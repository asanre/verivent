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
     * Analytics event that opens a new collapsible group in the timeline, or null for no grouping.
     * Read by every viewer: the Compose inspector, the Android Activity, the web page and the
     * native iOS hosts. Set it through [configure].
     */
    var groupByEvent: String? = DEFAULT_GROUP_BY_EVENT
        private set

    /**
     * Property of the [groupByEvent] event whose value labels the group — Firebase's convention for
     * a manual `screen_view` is `screen_name`. When it is null, or the event doesn't carry it, the
     * group falls back to the event's own name.
     */
    var groupLabelProperty: String? = DEFAULT_GROUP_LABEL_PROPERTY
        private set

    /**
     * Reconfigure the store limits and how the viewers group the timeline. Changing the limits
     * clears existing entries — set them before traffic starts; grouping changes leave the store
     * alone. The store and its StateFlow survive, so already-attached collectors (Compose UI, SSE)
     * keep working. [reset] restores the defaults.
     */
    fun configure(
        capacity: Int = store.capacity,
        maxTotalBytes: Long = store.maxTotalBytes,
        groupByEvent: String? = this.groupByEvent,
        groupLabelProperty: String? = this.groupLabelProperty,
    ) {
        this.groupByEvent = groupByEvent
        this.groupLabelProperty = groupLabelProperty
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

    /** Test/utility hook to reset all state, grouping config included. */
    fun reset() {
        idCounter.store(0)
        store.clear()
        enabled = true
        groupByEvent = DEFAULT_GROUP_BY_EVENT
        groupLabelProperty = DEFAULT_GROUP_LABEL_PROPERTY
    }

    private const val DEFAULT_GROUP_BY_EVENT = "screen_view"
    private const val DEFAULT_GROUP_LABEL_PROPERTY = "screen_name"

    val entriesSnapshot: List<SnoopEntry> get() = store.entries.value
}
