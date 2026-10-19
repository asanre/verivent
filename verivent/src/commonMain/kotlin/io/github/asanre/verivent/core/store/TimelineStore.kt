package io.github.asanre.verivent.core.store

import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.core.model.TimelineEntry
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory ring buffer of captured entries, exposed as a [StateFlow] the UI observes.
 * All mutations go through [mutate], which is safe under concurrent writers ([MutableStateFlow.update]
 * retries on conflict), so the Ktor plugin and the analytics logger can record from any thread.
 *
 * Two eviction limits apply, both drop-oldest: [capacity] (entry count) and [maxTotalBytes]
 * (approximate memory), so a burst of huge bodies can't balloon the host's heap.
 */
class TimelineStore(capacity: Int = DEFAULT_CAPACITY, maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES) {

    init {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
        require(maxTotalBytes > 0) { "maxTotalBytes must be > 0, was $maxTotalBytes" }
    }

    @Volatile
    var capacity: Int = capacity
        private set

    @Volatile
    var maxTotalBytes: Long = maxTotalBytes
        private set

    private val _entries = MutableStateFlow<List<TimelineEntry>>(emptyList())
    val entries: StateFlow<List<TimelineEntry>> get() = _entries

    /**
     * Change the limits in place. The same [entries] StateFlow survives, so UI and SSE collectors
     * keep working. With [clear] (the default) existing entries are dropped; otherwise the list is
     * trimmed to the new limits.
     */
    fun reconfigure(
        capacity: Int = this.capacity,
        maxTotalBytes: Long = this.maxTotalBytes,
        clear: Boolean = true,
    ) {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
        require(maxTotalBytes > 0) { "maxTotalBytes must be > 0, was $maxTotalBytes" }
        this.capacity = capacity
        this.maxTotalBytes = maxTotalBytes
        mutate { current -> if (clear) emptyList() else current.capped(capacity, maxTotalBytes) }
    }

    fun record(entry: TimelineEntry) = mutate { (it + entry).capped(capacity, maxTotalBytes) }

    // Also capped: a response arriving via update() is what balloons an entry's size.
    fun update(id: String, transform: (TimelineEntry) -> TimelineEntry) = mutate { list ->
        list.map { if (it.id == id) transform(it) else it }.capped(capacity, maxTotalBytes)
    }

    fun mutate(transform: (List<TimelineEntry>) -> List<TimelineEntry>) = _entries.update(transform)

    fun clear() {
        _entries.value = emptyList()
    }

    companion object {
        const val DEFAULT_CAPACITY = 500
        const val DEFAULT_MAX_TOTAL_BYTES = 20_000_000L
    }
}

private fun List<TimelineEntry>.capped(capacity: Int, maxTotalBytes: Long): List<TimelineEntry> {
    val overCapacity = (size - capacity).coerceAtLeast(0)
    var dropUntil = overCapacity
    var total = 0L
    // Newest-first, so the budget is spent on the entries we keep and the walk stops at the cut
    // instead of measuring the whole store. The newest entry stays even if it alone exceeds it.
    for (index in lastIndex downTo overCapacity) {
        total += this[index].approxBytes()
        if (total > maxTotalBytes && index < lastIndex) {
            dropUntil = index + 1
            break
        }
    }
    return if (dropUntil == 0) this else subList(dropUntil, size).toList()
}

private fun TimelineEntry.approxBytes(): Long = when (this) {
    is NetworkTransaction -> approxBytes
    is AnalyticsEntry -> approxBytes
}
