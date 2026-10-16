package com.snoop.core

import com.snoop.analytics.TrackingPlan
import com.snoop.core.internal.SnoopLog
import com.snoop.core.internal.deleteFile
import com.snoop.core.internal.readTextFile
import com.snoop.core.internal.writeTextFile
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.PlanSummary
import com.snoop.core.model.SnoopEntry
import com.snoop.core.store.SnoopStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * What every viewer calls itself — the inspector's header, the notification, the web page. Set
     * it through [configure] when the host would rather the tool carried its own name.
     */
    var title: String = DEFAULT_TITLE
        private set

    /**
     * Directory where Snoop keeps what must survive a restart (today, the tracking plan). Null keeps
     * everything in memory for the life of the process.
     */
    var storageDir: String? = null
        private set

    /**
     * Reconfigure the store limits, the name the viewers show and the [storageDir]. Changing the
     * limits clears existing entries — set them before traffic starts; changing the title leaves the
     * store alone. The store and its StateFlow survive, so already-attached collectors (Compose UI,
     * SSE) keep working. Setting a [storageDir] that holds a tracking plan loads it. [reset]
     * restores the defaults.
     */
    fun configure(
        capacity: Int = store.capacity,
        maxTotalBytes: Long = store.maxTotalBytes,
        title: String = this.title,
        storageDir: String? = this.storageDir,
    ) {
        this.title = title
        if (capacity != store.capacity || maxTotalBytes != store.maxTotalBytes) {
            store.reconfigure(capacity = capacity, maxTotalBytes = maxTotalBytes)
        }
        if (storageDir != this.storageDir) {
            this.storageDir = storageDir
            restoreTrackingPlan()
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

    // --- tracking plan ---

    @Volatile
    internal var activePlan: TrackingPlan? = null
        private set

    private val loadedPlan = MutableStateFlow<PlanSummary?>(null)

    /** The loaded tracking plan's version and events, or null when none is loaded. */
    val trackingPlan: StateFlow<PlanSummary?> = loadedPlan.asStateFlow()

    /**
     * Validate every analytics event captured from now on against this tracking plan, replacing the
     * current one, and keep it in [storageDir] when there is one. Events captured earlier keep the
     * verdict they were stamped with. The format is described in the analytics integration docs.
     *
     * @throws IllegalArgumentException when [json] isn't a valid tracking plan; nothing changes then.
     */
    fun loadTrackingPlan(json: String) {
        val plan = TrackingPlan.parse(json)
        install(plan)
        planFile()?.let { path ->
            runCatching { writeTextFile(path, json) }
                .onFailure { SnoopLog.warn(TAG, "could not keep the tracking plan; it lasts until the app restarts", it) }
        }
    }

    /** Stop validating, and forget the plan kept in [storageDir]. */
    fun clearTrackingPlan() {
        install(null)
        planFile()?.let { path -> runCatching { deleteFile(path) } }
    }

    private fun install(plan: TrackingPlan?) {
        activePlan = plan
        loadedPlan.value = plan?.summary
    }

    // A directory without a stored plan leaves the current one alone: it may have just been loaded.
    private fun restoreTrackingPlan() {
        val json = planFile()?.let { path -> runCatching { readTextFile(path) }.getOrNull() } ?: return
        runCatching { TrackingPlan.parse(json) }
            .onSuccess(::install)
            .onFailure { failure -> SnoopLog.warn(TAG, "ignoring the stored tracking plan", failure) }
    }

    private fun planFile(): String? = storageDir?.let { "${it.trimEnd('/')}/$PLAN_FILE" }

    /** Test/utility hook to reset all state. */
    fun reset() {
        idCounter.store(0)
        store.clear()
        enabled = true
        title = DEFAULT_TITLE
        storageDir = null
        install(null)
    }

    private const val DEFAULT_TITLE = "Snoop"
    private const val PLAN_FILE = "tracking-plan.json"
    private const val TAG = "core"

    val entriesSnapshot: List<SnoopEntry> get() = store.entries.value
}
