package io.github.asanre.verivent.core.store

import io.github.asanre.verivent.core.model.TimelineEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** No-op mirror: never stores anything; every instance exposes the same empty [StateFlow]. */
class TimelineStore(capacity: Int = DEFAULT_CAPACITY, maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES) {

    val capacity: Int = capacity

    val maxTotalBytes: Long = maxTotalBytes

    val entries: StateFlow<List<TimelineEntry>> = EMPTY

    fun reconfigure(
        capacity: Int = this.capacity,
        maxTotalBytes: Long = this.maxTotalBytes,
        clear: Boolean = true,
    ) = Unit

    fun record(entry: TimelineEntry) = Unit

    fun update(id: String, transform: (TimelineEntry) -> TimelineEntry) = Unit

    fun mutate(transform: (List<TimelineEntry>) -> List<TimelineEntry>) = Unit

    fun clear() = Unit

    companion object {
        const val DEFAULT_CAPACITY = 500
        const val DEFAULT_MAX_TOTAL_BYTES = 20_000_000L
        private val EMPTY = MutableStateFlow<List<TimelineEntry>>(emptyList())
    }
}
