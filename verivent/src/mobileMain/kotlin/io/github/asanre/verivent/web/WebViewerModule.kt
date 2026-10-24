package io.github.asanre.verivent.web

import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.InternalVeriventApi
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.TimelineEntry
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.delay
import kotlinx.io.readByteArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private const val TITLE_PLACEHOLDER = "@VERIVENT_TITLE@"

private fun escapeHtml(text: String): String = buildString(text.length) {
    for (char in text) {
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}

// Plans are a few hundred events at most; this only stops a runaway paste from filling memory.
private const val MAX_PLAN_BYTES = 2 * 1024 * 1024

/**
 * The body, or null past the size a tracking plan may have: one byte past the limit is read whether
 * or not the client declared a length.
 */
internal suspend fun ApplicationCall.receiveUpToPlanSize(): String? =
    receiveChannel().readRemaining(MAX_PLAN_BYTES + 1L).readByteArray().takeIf { it.size <= MAX_PLAN_BYTES }?.decodeToString()

private fun planStatus(): String = buildJsonObject {
    val plan = Inspector.trackingPlan.value
    put("version", plan?.version)
    putJsonArray("events") {
        plan?.events?.forEach { event ->
            addJsonObject {
                put("name", event.name)
                put("channel", event.channel)
                plan?.variants?.get(event)?.let { ids -> putJsonArray("variants") { ids.forEach { add(it) } } }
            }
        }
    }
}.toString()

/**
 * CSRF guard for every state-changing request: the custom header forces a preflight this server
 * never approves, and any cross-origin caller (a hostile page open in the same browser) carries a
 * foreign Origin. Same-origin is judged against the Host actually used, which the gate vouches for.
 */
private fun ApplicationCall.mutationAllowed(): Boolean {
    val origin = request.header(HttpHeaders.Origin)
    val host = request.header(HttpHeaders.Host)
    return request.header("X-Verivent") == "1" && (origin == null || origin == "http://$host")
}

@OptIn(InternalVeriventApi::class)
internal fun Application.webViewerModule(token: String? = null) {
    install(SSE)
    // One gate in front of routing, so a route added later can't ship unguarded.
    intercept(ApplicationCallPipeline.Plugins) {
        if (!hostHeaderAllowed(call.request.header(HttpHeaders.Host))) {
            call.respondText("forbidden", status = HttpStatusCode.Forbidden)
            return@intercept finish()
        }
        if (token != null && !tokensEqual(token, call.request.queryParameters["t"].orEmpty())) {
            call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            return@intercept finish()
        }
        if (call.request.httpMethod != HttpMethod.Get && !call.mutationAllowed()) {
            call.respondText("forbidden", status = HttpStatusCode.Forbidden)
            return@intercept finish()
        }
    }
    routing {
        get("/") {
            // Substituted here rather than at build time: the title is runtime config, and it is
            // host-supplied text landing in markup, so it is escaped rather than trusted.
            call.respondText(INDEX_HTML.replace(TITLE_PLACEHOLDER, escapeHtml(Inspector.title)), ContentType.Text.Html)
        }
        get("/api/entries") {
            call.respondText(encodeEntries(Inspector.store.entries.value), ContentType.Application.Json)
        }
        get("/api/export") {
            val events = Inspector.store.entries.value.filterIsInstance<AnalyticsEntry>()
            call.respondText(encodeExport(events), ContentType.Application.Json)
        }
        post("/api/clear") {
            Inspector.store.clear()
            call.respondText("ok")
        }
        get("/api/tracking-plan") {
            call.respondText(planStatus(), ContentType.Application.Json)
        }
        post("/api/tracking-plan") {
            val json = call.receiveUpToPlanSize()
                ?: return@post call.respondText("tracking plan too large", status = HttpStatusCode.PayloadTooLarge)
            try {
                Inspector.loadTrackingPlan(json)
            } catch (invalid: IllegalArgumentException) {
                return@post call.respondText(invalid.message.orEmpty(), status = HttpStatusCode.BadRequest)
            }
            call.respondText(planStatus(), ContentType.Application.Json)
        }
        post("/api/tracking-plan/clear") {
            Inspector.clearTrackingPlan()
            call.respondText(planStatus(), ContentType.Application.Json)
        }
        // Black-box test assertions: a test that only speaks HTTP
        // (through `adb forward` or the simulator's localhost) asks whether any event broke the
        // plan. `POST /api/clear` resets between tests.
        get("/api/assert/all") {
            call.respondText(assertionSummary(Inspector.store.entries.value, Inspector.trackingPlan.value), ContentType.Application.Json)
        }
        get("/api/assert/{verdict}") {
            val verdict = Verdict.entries.firstOrNull { it.route == call.parameters["verdict"] }
                ?: return@get call.respondText("no such verdict: good, bad or unplanned", status = HttpStatusCode.NotFound)
            val matching = assertionEntries(
                entries = Inspector.store.entries.value,
                plan = Inspector.trackingPlan.value,
                verdict = verdict,
                name = call.request.queryParameters["name"],
                channel = call.request.queryParameters["channel"],
            )
            call.respondText(encodeEntries(matching), ContentType.Application.Json)
        }
        baselineRoutes()
        sse("/api/stream") {
            // Deltas, tracked per connection: the store holds up to 20 MB and re-encoding all of it
            // on every mutation is the host's bill to pay. `TimelineStore.update` replaces only the
            // entry it touches, so reference identity is enough to tell what the client is missing.
            val last = HashMap<String, TimelineEntry>()
            // StateFlow collection is conflated by design: during the delay intermediate bursts
            // collapse and the collector resumes on the latest state, so encoding is capped at
            // ~4 frames/s and the final state is always delivered.
            Inspector.store.entries.collect { entries ->
                val ids = ArrayList<String>(entries.size)
                val changed = ArrayList<TimelineEntry>()
                for (entry in entries) {
                    ids += entry.id
                    if (last.put(entry.id, entry) !== entry) changed += entry
                }
                if (last.size > ids.size) last.keys.retainAll(ids.toHashSet())
                send(ServerSentEvent(data = encodeFrame(ids, changed)))
                delay(250)
            }
        }
    }
}
