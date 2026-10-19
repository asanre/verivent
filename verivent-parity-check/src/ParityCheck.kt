@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UnusedPrivateMember")

package io.github.asanre.verivent.paritycheck

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.asanre.verivent.analytics.AnalyticsEvent
import io.github.asanre.verivent.analytics.AnalyticsSink
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.InternalVeriventApi
import io.github.asanre.verivent.core.capture.BodyCapture
import io.github.asanre.verivent.core.internal.DiagnosticLog
import io.github.asanre.verivent.core.internal.runSafely
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.Annotation
import io.github.asanre.verivent.core.model.BodyPreview
import io.github.asanre.verivent.core.model.KeyValue
import io.github.asanre.verivent.core.model.NetworkState
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.core.model.PlanEvent
import io.github.asanre.verivent.core.model.PlanIssue
import io.github.asanre.verivent.core.model.PlanSummary
import io.github.asanre.verivent.core.model.TimelineEntry
import io.github.asanre.verivent.core.redaction.RedactionEngine
import io.github.asanre.verivent.core.store.TimelineStore
import io.github.asanre.verivent.ktor.Verivent as VeriventPlugin
import io.github.asanre.verivent.ktor.VeriventConfig
import io.github.asanre.verivent.ui.InspectorLauncher as LauncherEntryPoints
import io.github.asanre.verivent.ui.InspectorScreen
import io.github.asanre.verivent.web.WebViewerBind
import io.github.asanre.verivent.web.WebViewer
import io.github.asanre.verivent.web.encodeExportEntry
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow

/*
 * Parity enforcement: this file references every symbol the no-op modules mirror, with exact
 * types, and is compiled twice — once against the real modules (:verivent-parity-check:real) and once
 * against the no-op modules (:verivent-parity-check:noop). API drift in either direction is a compile
 * failure in `./gradlew check`. Never executed. `InspectorActivity` is intentionally out of scope:
 * hosts reach it only through `InspectorLauncher.open`, which is mirrored.
 */

private fun inspector(txn: NetworkTransaction, event: AnalyticsEntry) {
    val store: TimelineStore = Inspector.store
    val enabled: Boolean = Inspector.enabled
    Inspector.enabled = enabled
    Inspector.configure()
    Inspector.configure(capacity = 100)
    Inspector.configure(capacity = 100, maxTotalBytes = 1_000L)
    Inspector.configure(title = "Patata Inspector")
    Inspector.configure(storageDir = "/data/verivent")
    Inspector.configure(storageDir = null)
    val storageDir: String? = Inspector.storageDir
    Inspector.loadTrackingPlan("{}")
    Inspector.clearTrackingPlan()
    val planFlow: StateFlow<PlanSummary?> = Inspector.trackingPlan
    val plan: PlanSummary? = planFlow.value
    val planVersion: String? = plan?.version
    val planEvents: List<PlanEvent>? = plan?.events
    val planEvent: PlanEvent = PlanEvent(name = "e", channel = "c")
    val planEventName: String = planEvent.name
    val planEventChannel: String = planEvent.channel
    val summary: PlanSummary = PlanSummary(version = "1", events = listOf(planEvent))
    val title: String = Inspector.title
    val id: String = Inspector.nextId()
    val now: Instant = Inspector.now()
    Inspector.recordNetwork(txn)
    Inspector.updateNetwork("id") { transaction: NetworkTransaction -> transaction }
    Inspector.recordAnalytics(event)
    Inspector.reset()
    val snapshot: List<TimelineEntry> = Inspector.entriesSnapshot
}

private fun internals() {
    runSafely("tag") { }
    DiagnosticLog.warn("tag", "message")
    DiagnosticLog.warn("tag", "message", RuntimeException())
    DiagnosticLog.warn(tag = "tag", message = "message", throwable = null)
    DiagnosticLog.info("tag", "message")
}

private fun model() {
    val kv = KeyValue(name = "n", value = "v", redacted = false, type = "string")
    val kvType: String? = kv.type
    val body = BodyPreview(text = "t", byteCount = 1L, truncated = false)
    val isEmpty: Boolean = body.isEmpty
    val states = listOf(NetworkState.Pending, NetworkState.Complete, NetworkState.Failed)
    val txn = NetworkTransaction(
        id = "1",
        timestamp = Instant.fromEpochMilliseconds(0),
        method = "GET",
        url = "u",
        host = "h",
        path = "/",
        requestContentType = null,
        requestHeaders = listOf(kv),
        requestBody = body,
        status = 200,
        responseContentType = null,
        responseHeaders = emptyList(),
        responseBody = null,
        protocol = null,
        durationMillis = 1L,
        error = null,
    )
    val state: NetworkState = txn.state
    val event = AnalyticsEntry(
        id = "2",
        timestamp = Instant.fromEpochMilliseconds(0),
        name = "e",
        channel = "c",
        properties = listOf(kv),
        annotations = listOf(
            Annotation.Ecommerce,
            Annotation.Conversion,
            Annotation.Consent,
            Annotation.ScreenView,
            Annotation("custom"),
        ),
        groupLabel = "Home",
        sections = mapOf("User properties" to listOf(kv)),
        planVersion = "1.0.0",
        issues = listOf(PlanIssue(property = "k", message = "Required, but not sent")),
    )
    val entryPlanVersion: String? = event.planVersion
    val issues: List<PlanIssue> = event.issues
    val issueProperty: String = issues.first().property
    val issueMessage: String = issues.first().message
    val groupLabel: String? = event.groupLabel
    val sections: Map<String, List<KeyValue>> = event.sections
    val label: String = Annotation.Ecommerce.label
    val entries: List<TimelineEntry> = listOf(txn, event)
    entries.forEach { entry ->
        val entryId: String = entry.id
        val at: Instant = entry.timestamp
    }
}

private fun store(entry: TimelineEntry) {
    val store = TimelineStore()
    val sized = TimelineStore(capacity = 10, maxTotalBytes = 1_000L)
    val capacity: Int = store.capacity
    val budget: Long = store.maxTotalBytes
    val flow: StateFlow<List<TimelineEntry>> = store.entries
    store.reconfigure(capacity = 10)
    store.reconfigure(capacity = 10, maxTotalBytes = 1_000L, clear = false)
    store.record(entry)
    store.update("id") { it }
    store.mutate { it }
    store.clear()
    val defaultCapacity: Int = TimelineStore.DEFAULT_CAPACITY
    val defaultBytes: Long = TimelineStore.DEFAULT_MAX_TOTAL_BYTES
}

private fun bodyCapture() {
    val text: Boolean = BodyCapture.isTextLike("application/json")
    // Both call shapes on purpose: the second checks the mirror kept the default argument too.
    val counted: BodyPreview = BodyCapture.textPreview(ByteArray(0), "text/plain", 10L, 3L)
    val defaulted: BodyPreview = BodyCapture.textPreview(ByteArray(0), null, 10L)
    val binary: BodyPreview = BodyCapture.binaryPlaceholder("image/png", 3L)
}

private fun redaction() {
    val default = RedactionEngine()
    val custom = RedactionEngine(
        headerPredicates = listOf { it == "x" },
        propertyPredicates = listOf { it == "x" },
        queryParamPredicates = listOf { it == "x" },
        bodyTransforms = listOf { it },
        placeholder = "*",
    )
    val header: KeyValue = custom.redactHeader("n", "v")
    val property: KeyValue = custom.redactProperty("k", "v")
    val url: String = custom.redactUrl("https://x?token=y")
    val form: String = custom.redactFormBody("token=y&a=b")
    val body: String = custom.redactBody("b")
    val placeholder: String = RedactionEngine.DEFAULT_PLACEHOLDER
    val headers: Set<String> = RedactionEngine.DEFAULT_HEADERS
    val queryParams: Set<String> = RedactionEngine.DEFAULT_QUERY_PARAMS
}

private fun ktorPlugin(config: VeriventConfig) {
    val plugin: ClientPlugin<VeriventConfig> = VeriventPlugin
    config.maxContentLength = 1L
    val max: Long = config.maxContentLength
    config.sanitizeHeader { it == "x" }
    config.sanitizeQueryParameter { it == "x" }
    config.sanitizeBody { it }
    config.filter { request: HttpRequestBuilder -> true }
}

private fun analytics() {
    val plain: AnalyticsSink = AnalyticsSink()
    val configured: AnalyticsSink = AnalyticsSink {
        filter { event: AnalyticsEvent ->
            event.name.isNotEmpty() && event.channel.isNotEmpty() && event.properties.isEmpty()
        }
        sanitizeProperty { it == "x" }
        annotate { listOf(Annotation.Ecommerce) }
        groupBy("screen_view")
        groupBy(event = null, labelProperty = null)
        logTo()
        logTo { line: String -> }
    }
    configured.log("name", "channel")
    configured.log("name", "channel", mapOf("k" to 1))
    configured.log("name", "channel", sections = mapOf("User properties" to mapOf("k" to 1)))
    val event = AnalyticsEvent(name = "n", channel = "c", properties = mapOf("k" to null))
}

private fun webServer() {
    val started: String = WebViewer.start()
    val onPort: String = WebViewer.start(port = 1234)
    val onLan: String = WebViewer.start(WebViewerBind.Lan, port = 1234, token = "t")
    val onHost: String = WebViewer.start(WebViewerBind.Host("192.168.1.5"))
    val local: WebViewerBind = WebViewerBind.Local
    val hostAddress: String = WebViewerBind.Host("x").address
    val running: Boolean = WebViewer.isRunning
    val url: String = WebViewer.url
    val token: String? = WebViewer.token
    val lanUrl: String? = WebViewer.lanUrl()
    WebViewer.stop()
    val defaultPort: Int = WebViewer.DEFAULT_PORT
}

private fun androidEntryPoints(context: Context) {
    LauncherEntryPoints.open(context)
    LauncherEntryPoints.showNotification(context)
    LauncherEntryPoints.dismissNotification(context)
}

@Composable
private fun screen() {
    InspectorScreen()
    InspectorScreen(modifier = Modifier)
    InspectorScreen(onClose = {})
    InspectorScreen(modifier = Modifier, onClose = null)
}

@OptIn(InternalVeriventApi::class)
private fun export(entry: TimelineEntry) {
    val json: String = encodeExportEntry(entry)
}
