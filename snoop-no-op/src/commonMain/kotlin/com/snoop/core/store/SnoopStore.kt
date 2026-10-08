package com.snoop.core.store

import com.snoop.core.model.SnoopEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** No-op mirror: never stores anything; every instance exposes the same empty [StateFlow]. */
class SnoopStore(capacity: Int = DEFAULT_CAPACITY, maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES) {

    val capacity: Int = capacity

    val maxTotalBytes: Long = maxTotalBytes

    val entries: StateFlow<List<SnoopEntry>> = EMPTY

    fun reconfigure(
        capacity: Int = this.capacity,
        maxTotalBytes: Long = this.maxTotalBytes,
        clear: Boolean = true,
    ) = Unit

    fun record(entry: SnoopEntry) = Unit

    fun update(id: String, transform: (SnoopEntry) -> SnoopEntry) = Unit

    fun mutate(transform: (List<SnoopEntry>) -> List<SnoopEntry>) = Unit

    fun clear() = Unit

    companion object {
        const val DEFAULT_CAPACITY = 500
        const val DEFAULT_MAX_TOTAL_BYTES = 20_000_000L
        private val EMPTY = MutableStateFlow<List<SnoopEntry>>(emptyList())
    }
}
