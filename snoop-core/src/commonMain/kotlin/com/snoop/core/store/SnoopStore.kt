package com.snoop.core.store

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
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
class SnoopStore(capacity: Int = DEFAULT_CAPACITY, maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES) {

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

    private val _entries = MutableStateFlow<List<SnoopEntry>>(emptyList())
    val entries: StateFlow<List<SnoopEntry>> get() = _entries

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

    fun record(entry: SnoopEntry) = mutate { (it + entry).capped(capacity, maxTotalBytes) }

    // Also capped: a response arriving via update() is what balloons an entry's size.
    fun update(id: String, transform: (SnoopEntry) -> SnoopEntry) = mutate { list ->
        list.map { if (it.id == id) transform(it) else it }.capped(capacity, maxTotalBytes)
    }

    fun mutate(transform: (List<SnoopEntry>) -> List<SnoopEntry>) = _entries.update(transform)

    fun clear() {
        _entries.value = emptyList()
    }

    companion object {
        const val DEFAULT_CAPACITY = 500
        const val DEFAULT_MAX_TOTAL_BYTES = 20_000_000L
    }
}

private fun List<SnoopEntry>.capped(capacity: Int, maxTotalBytes: Long): List<SnoopEntry> {
    var dropUntil = (size - capacity).coerceAtLeast(0)
    var total = sumOf { it.approxBytes() } - subList(0, dropUntil).sumOf { it.approxBytes() }
    // Keep at least the newest entry even if it alone exceeds the budget.
    while (total > maxTotalBytes && dropUntil < size - 1) {
        total -= this[dropUntil].approxBytes()
        dropUntil++
    }
    return if (dropUntil == 0) this else subList(dropUntil, size).toList()
}

private fun SnoopEntry.approxBytes(): Long = when (this) {
    is NetworkTransaction ->
        ENTRY_OVERHEAD_BYTES + url.length +
            (requestBody?.text?.length ?: 0) + (responseBody?.text?.length ?: 0) +
            requestHeaders.sumOf { it.name.length + it.value.length } +
            responseHeaders.sumOf { it.name.length + it.value.length }
    is AnalyticsEntry ->
        ENTRY_OVERHEAD_BYTES + name.length + channel.length +
            properties.sumOf { it.name.length + it.value.length }
}.toLong()

private const val ENTRY_OVERHEAD_BYTES = 256
