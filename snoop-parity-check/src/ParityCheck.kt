@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UnusedPrivateMember")

package com.snoop.paritycheck

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.snoop.analytics.AnalyticsEvent
import com.snoop.analytics.SnoopAnalytics
import com.snoop.core.SnoopCore
import com.snoop.core.capture.BodyCapture
import com.snoop.core.internal.SnoopLog
import com.snoop.core.internal.runSafely
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.Annotation
import com.snoop.core.model.BodyPreview
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkState
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import com.snoop.core.redaction.RedactionEngine
import com.snoop.core.store.SnoopStore
import com.snoop.ktor.Snoop as SnoopPlugin
import com.snoop.ktor.SnoopConfig
import com.snoop.ui.Snoop as SnoopEntryPoints
import com.snoop.ui.SnoopScreen
import com.snoop.web.SnoopBind
import com.snoop.web.SnoopWebServer
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow

/*
 * Parity enforcement: this file references every symbol the no-op modules mirror, with exact
 * types, and is compiled twice — once against the real modules (:snoop-parity-check:real) and once
 * against the no-op modules (:snoop-parity-check:noop). API drift in either direction is a compile
 * failure in `./gradlew check`. Never executed. `SnoopActivity` is intentionally out of scope:
 * hosts reach it only through `Snoop.open`, which is mirrored.
 */

private fun snoopCore(txn: NetworkTransaction, event: AnalyticsEntry) {
    val store: SnoopStore = SnoopCore.store
    val enabled: Boolean = SnoopCore.enabled
    SnoopCore.enabled = enabled
    SnoopCore.configure()
    SnoopCore.configure(capacity = 100)
    SnoopCore.configure(capacity = 100, maxTotalBytes = 1_000L)
    SnoopCore.configure(title = "Patata Inspector")
    val title: String = SnoopCore.title
    val id: String = SnoopCore.nextId()
    val now: Instant = SnoopCore.now()
    SnoopCore.recordNetwork(txn)
    SnoopCore.updateNetwork("id") { transaction: NetworkTransaction -> transaction }
    SnoopCore.recordAnalytics(event)
    SnoopCore.reset()
    val snapshot: List<SnoopEntry> = SnoopCore.entriesSnapshot
}

private fun internals() {
    runSafely("tag") { }
    SnoopLog.warn("tag", "message")
    SnoopLog.warn("tag", "message", RuntimeException())
    SnoopLog.warn(tag = "tag", message = "message", throwable = null)
    SnoopLog.info("tag", "message")
}

private fun model() {
    val kv = KeyValue(name = "n", value = "v", redacted = false)
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
    )
    val groupLabel: String? = event.groupLabel
    val label: String = Annotation.Ecommerce.label
    val entries: List<SnoopEntry> = listOf(txn, event)
    entries.forEach { entry ->
        val entryId: String = entry.id
        val at: Instant = entry.timestamp
    }
}

private fun store(entry: SnoopEntry) {
    val store = SnoopStore()
    val sized = SnoopStore(capacity = 10, maxTotalBytes = 1_000L)
    val capacity: Int = store.capacity
    val budget: Long = store.maxTotalBytes
    val flow: StateFlow<List<SnoopEntry>> = store.entries
    store.reconfigure(capacity = 10)
    store.reconfigure(capacity = 10, maxTotalBytes = 1_000L, clear = false)
    store.record(entry)
    store.update("id") { it }
    store.mutate { it }
    store.clear()
    val defaultCapacity: Int = SnoopStore.DEFAULT_CAPACITY
    val defaultBytes: Long = SnoopStore.DEFAULT_MAX_TOTAL_BYTES
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

private fun ktorPlugin(config: SnoopConfig) {
    val plugin: ClientPlugin<SnoopConfig> = SnoopPlugin
    config.maxContentLength = 1L
    val max: Long = config.maxContentLength
    config.sanitizeHeader { it == "x" }
    config.sanitizeQueryParameter { it == "x" }
    config.sanitizeBody { it }
    config.filter { request: HttpRequestBuilder -> true }
}

private fun analytics() {
    val plain: SnoopAnalytics = SnoopAnalytics()
    val configured: SnoopAnalytics = SnoopAnalytics {
        filter { event: AnalyticsEvent ->
            event.name.isNotEmpty() && event.channel.isNotEmpty() && event.properties.isEmpty()
        }
        sanitizeProperty { it == "x" }
        annotate { listOf(Annotation.Ecommerce) }
        groupBy("screen_view")
        groupBy(event = null, labelProperty = null)
    }
    configured.log("name", "channel")
    configured.log("name", "channel", mapOf("k" to 1))
    val event = AnalyticsEvent(name = "n", channel = "c", properties = mapOf("k" to null))
}

private fun webServer() {
    val started: String = SnoopWebServer.start()
    val onPort: String = SnoopWebServer.start(port = 1234)
    val onLan: String = SnoopWebServer.start(SnoopBind.Lan, port = 1234, token = "t")
    val onHost: String = SnoopWebServer.start(SnoopBind.Host("192.168.1.5"))
    val local: SnoopBind = SnoopBind.Local
    val hostAddress: String = SnoopBind.Host("x").address
    val running: Boolean = SnoopWebServer.isRunning
    val url: String = SnoopWebServer.url
    val token: String? = SnoopWebServer.token
    val lanUrl: String? = SnoopWebServer.lanUrl()
    SnoopWebServer.stop()
    val defaultPort: Int = SnoopWebServer.DEFAULT_PORT
}

private fun androidEntryPoints(context: Context) {
    SnoopEntryPoints.open(context)
    SnoopEntryPoints.showNotification(context)
    SnoopEntryPoints.dismissNotification(context)
}

@Composable
private fun screen() {
    SnoopScreen()
    SnoopScreen(modifier = Modifier)
}
