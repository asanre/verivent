package com.snoop.core

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import com.snoop.core.store.SnoopStore
import kotlin.time.Instant

/** No-op mirror of `SnoopCore` for release builds: records nothing, stores nothing. */
object SnoopCore {

    val store: SnoopStore = SnoopStore()

    var enabled: Boolean = false

    var title: String = "Snoop"
        private set

    fun configure(
        capacity: Int = store.capacity,
        maxTotalBytes: Long = store.maxTotalBytes,
        title: String = this.title,
    ) = Unit

    fun nextId(): String = "0"

    fun now(): Instant = Instant.fromEpochMilliseconds(0)

    fun recordNetwork(transaction: NetworkTransaction) = Unit

    fun updateNetwork(id: String, transform: (NetworkTransaction) -> NetworkTransaction) = Unit

    fun recordAnalytics(entry: AnalyticsEntry) = Unit

    fun reset() = Unit

    val entriesSnapshot: List<SnoopEntry> get() = emptyList()
}
