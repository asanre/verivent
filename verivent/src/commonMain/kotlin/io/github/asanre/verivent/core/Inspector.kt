package io.github.asanre.verivent.core

import io.github.asanre.verivent.analytics.TrackingPlan
import io.github.asanre.verivent.core.internal.DiagnosticLog
import io.github.asanre.verivent.core.internal.deleteFile
import io.github.asanre.verivent.core.internal.readTextFile
import io.github.asanre.verivent.core.internal.writeTextFile
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.core.model.PlanSummary
import io.github.asanre.verivent.core.model.TimelineEntry
import io.github.asanre.verivent.core.store.TimelineStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Central, DI-agnostic entry point that the capture adapters (Ktor, analytics) write to and the UI
 * reads from. Holds the single shared [TimelineStore]. Adapters never reference the UI or each other —
 * they only talk to this bridge, keeping capture, storage and UI fully decoupled.
 */
@OptIn(ExperimentalAtomicApi::class)
object Inspector {

    private val idCounter = AtomicLong(0)

    val store: TimelineStore = TimelineStore()

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
     * Directory where Verivent keeps what must survive a restart (today, the tracking plan). Null keeps
     * everything in memory for the life of the process.
     */
    var storageDir: String? = null
        private set

    /**
     * Reconfigure the store limits, the name the viewers show and the [storageDir]. Changing the
     * limits clears existing entries — set them before traffic starts; changing the title leaves the
     * store alone. The store and its StateFlow survive, so already-attached collectors (Compose UI,
     * SSE) keep working. Setting a [storageDir] that holds a tracking plan loads it in the background,
     * unless a plan is loaded or cleared first. [reset] restores the defaults.
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

    // A fresh holder per install, so a restore that started earlier can tell — by identity — that
    // something was loaded, cleared or reset meanwhile and drop what it read.
    private class Installed(val plan: TrackingPlan?)

    private val installed = AtomicReference(Installed(null))

    internal val activePlan: TrackingPlan? get() = installed.load().plan

    private val loadedPlan = MutableStateFlow<PlanSummary?>(null)

    /** The loaded tracking plan's version and events, or null when none is loaded. */
    val trackingPlan: StateFlow<PlanSummary?> = loadedPlan.asStateFlow()

    /**
     * Every read, write and delete of the stored plan, one at a time and in call order: a plan can be
     * megabytes of JSON, and the host usually configures and clears from the main thread.
     */
    internal val storage = Dispatchers.Default.limitedParallelism(1)

    private val storageScope = CoroutineScope(SupervisorJob() + storage)

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
            storageScope.launch {
                runCatching { writeTextFile(path, json) }
                    .onFailure { DiagnosticLog.warn(TAG, "could not keep the tracking plan; it lasts until the app restarts", it) }
            }
        }
    }

    /** Stop validating, and forget the plan kept in [storageDir]. */
    fun clearTrackingPlan() {
        install(null)
        planFile()?.let { path -> storageScope.launch { runCatching { deleteFile(path) } } }
    }

    private fun install(plan: TrackingPlan?) {
        installed.store(Installed(plan))
        publish()
    }

    // Re-reads instead of taking the plan, so writers that race converge on the last one installed.
    private fun publish() = loadedPlan.update { installed.load().plan?.summary }

    // A directory without a stored plan leaves the current one alone: it may have just been loaded.
    private fun restoreTrackingPlan() {
        val path = planFile() ?: return
        val seen = installed.load()
        storageScope.launch {
            val json = runCatching { readTextFile(path) }.getOrNull() ?: return@launch
            runCatching { TrackingPlan.parse(json) }
                .onSuccess { plan -> if (installed.compareAndSet(seen, Installed(plan))) publish() }
                .onFailure { failure -> DiagnosticLog.warn(TAG, "ignoring the stored tracking plan", failure) }
        }
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

    private const val DEFAULT_TITLE = "Verivent"
    private const val PLAN_FILE = "tracking-plan.json"
    private const val TAG = "core"

    val entriesSnapshot: List<TimelineEntry> get() = store.entries.value
}
