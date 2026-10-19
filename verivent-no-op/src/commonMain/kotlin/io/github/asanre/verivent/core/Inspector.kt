package io.github.asanre.verivent.core

import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.core.model.PlanSummary
import io.github.asanre.verivent.core.model.TimelineEntry
import io.github.asanre.verivent.core.store.TimelineStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/** No-op mirror of `Inspector` for release builds: records nothing, stores nothing. */
object Inspector {

    val store: TimelineStore = TimelineStore()

    var enabled: Boolean = false

    var title: String = "Verivent"
        private set

    var storageDir: String? = null
        private set

    fun configure(
        capacity: Int = store.capacity,
        maxTotalBytes: Long = store.maxTotalBytes,
        title: String = this.title,
        storageDir: String? = this.storageDir,
    ) = Unit

    fun nextId(): String = "0"

    fun now(): Instant = Instant.fromEpochMilliseconds(0)

    fun recordNetwork(transaction: NetworkTransaction) = Unit

    fun updateNetwork(id: String, transform: (NetworkTransaction) -> NetworkTransaction) = Unit

    fun recordAnalytics(entry: AnalyticsEntry) = Unit

    val trackingPlan: StateFlow<PlanSummary?> = MutableStateFlow(null)

    fun loadTrackingPlan(json: String) = Unit

    fun clearTrackingPlan() = Unit

    fun reset() = Unit

    val entriesSnapshot: List<TimelineEntry> get() = emptyList()
}
