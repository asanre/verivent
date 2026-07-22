package com.snoop.core.store

import com.snoop.core.model.SnoopEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory ring buffer of captured entries, exposed as a [StateFlow] the UI observes.
 * All mutations go through [mutate], which is safe under concurrent writers ([MutableStateFlow.update]
 * retries on conflict), so the Ktor plugin and the analytics logger can record from any thread.
 */
class SnoopStore(val capacity: Int = DEFAULT_CAPACITY) {

    private val _entries = MutableStateFlow<List<SnoopEntry>>(emptyList())
    val entries: StateFlow<List<SnoopEntry>> get() = _entries

    fun record(entry: SnoopEntry) = mutate { it.appendCapped(entry, capacity) }

    fun update(id: String, transform: (SnoopEntry) -> SnoopEntry) = mutate { list ->
        list.map { if (it.id == id) transform(it) else it }
    }

    fun mutate(transform: (List<SnoopEntry>) -> List<SnoopEntry>) = _entries.update(transform)

    fun clear() {
        _entries.value = emptyList()
    }

    companion object {
        const val DEFAULT_CAPACITY = 500
    }
}

private fun List<SnoopEntry>.appendCapped(entry: SnoopEntry, capacity: Int): List<SnoopEntry> {
    val next = this + entry
    return if (next.size > capacity) next.subList(next.size - capacity, next.size).toList() else next
}
