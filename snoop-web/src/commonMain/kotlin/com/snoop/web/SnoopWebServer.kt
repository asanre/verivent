package com.snoop.web

import com.snoop.core.SnoopCore
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.delay

/**
 * A tiny embedded HTTP server that serves the Snoop timeline as a live web page, so you can inspect
 * traffic from a desktop browser while debugging.
 *
 * Binds loopback by default (`adb forward tcp:9394` on Android; `http://localhost:9394` on the iOS
 * simulator, which shares the host loopback). Pass `host = "0.0.0.0"` to reach a physical device
 * from your Mac over Wi-Fi via [lanUrl]; those binds are always token-gated. Reads the same shared
 * [SnoopCore] store the on-device UI uses.
 */
object SnoopWebServer {

    private val running = AtomicReference<Running?>(null)

    val isRunning: Boolean get() = running.load() != null

    val url: String get() = urlOf(running.load())

    /**
     * The access token every request must carry as `?t=`, or null when the server is open — which
     * only happens on loopback binds. LAN binds always have one; see [start].
     */
    val token: String? get() = running.load()?.token

    /**
     * The URL to reach this device from another machine on the LAN, or null when bound to loopback
     * (nothing is listening off-device) or when no LAN address is available. Includes the token.
     */
    fun lanUrl(): String? = lanUrlOf(running.load(), localHostAddress())

    /**
     * Start the server (idempotent, thread-safe). Returns the local URL.
     *
     * @param host bind address. Defaults to loopback; pass `"0.0.0.0"` to reach it from another
     *   machine on the LAN (see [lanUrl]).
     * @param token when non-null, every request must carry a matching `?t=` query parameter.
     *   Loopback binds are open when this is null; LAN binds get a generated token instead, so
     *   captured traffic is never served to the whole network unauthenticated — read it from [token].
     */
    fun start(port: Int = DEFAULT_PORT, host: String = LOOPBACK, token: String? = null): String {
        val resolvedToken = effectiveToken(host, token)
        val candidate = runCatching { createServer(port, host, resolvedToken) }.getOrNull() ?: return url
        val started = Running(candidate, port, host, resolvedToken)
        if (!running.compareAndSet(null, started)) return url
        // Bind failure (port in use, restricted env) must never crash the host app.
        runCatching { candidate.start(wait = false) }
            .onFailure { running.compareAndSet(started, null) }
        return urlOf(started)
    }

    fun stop() {
        val started = running.exchange(null) ?: return
        started.server.stop(gracePeriodMillis = 300, timeoutMillis = 1000)
    }

    internal fun createServer(port: Int, host: String = LOOPBACK, token: String? = null): EmbeddedServer<*, *> =
        embeddedServer(CIO, host = host, port = port) { snoopModule(token) }

    const val DEFAULT_PORT: Int = 9394
    internal const val LOOPBACK: String = "127.0.0.1"
}

/** Everything that describes a live server, swapped in and out as one value so it can't half-update. */
internal data class Running(
    val server: EmbeddedServer<*, *>,
    val port: Int,
    val host: String,
    val token: String?,
)

internal fun urlOf(running: Running?): String =
    "http://localhost:${running?.port ?: SnoopWebServer.DEFAULT_PORT}"

internal fun lanUrlOf(running: Running?, address: String?): String? {
    if (running == null || running.host == SnoopWebServer.LOOPBACK || address == null) return null
    val base = "http://$address:${running.port}"
    return running.token?.let { "$base/?t=$it" } ?: base
}

/** A LAN bind is never open: without a caller-supplied token it gets a generated one. */
internal fun effectiveToken(host: String, token: String?): String? = when {
    token != null -> token
    host == SnoopWebServer.LOOPBACK -> null
    else -> secureRandomToken()
}

/**
 * DNS-rebinding guard. A hostile page can point its own domain at this device and then talk to us
 * same-origin, which slips past both CORS and the `Origin` check below. Reaching a debug server on
 * a device legitimately only ever happens through `localhost` or a literal address, so a request
 * carrying any real hostname is refused.
 */
internal fun hostHeaderAllowed(host: String?): Boolean {
    if (host.isNullOrEmpty()) return true
    val name = if (host.startsWith("[")) host.substringAfter('[').substringBefore(']') else host.substringBefore(':')
    return name == "localhost" || name.isAddressLiteral()
}

private fun String.isAddressLiteral(): Boolean = when {
    ':' in this -> all { it.isDigit() || it in "abcdefABCDEF:." }
    else -> '.' in this && all { it.isDigit() || it == '.' }
}

/** Constant-time compare, so a LAN attacker can't recover the token by timing prefixes. */
internal fun tokensEqual(expected: String, actual: String): Boolean {
    val a = expected.encodeToByteArray()
    val b = actual.encodeToByteArray()
    if (b.isEmpty()) return a.isEmpty()
    var diff = a.size xor b.size
    for (index in a.indices) diff = diff or (a[index].toInt() xor b[index % b.size].toInt())
    return diff == 0
}

internal fun Application.snoopModule(token: String? = null) {
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
    }
    routing {
        get("/") {
            call.respondText(INDEX_HTML, ContentType.Text.Html)
        }
        get("/api/entries") {
            call.respondText(encodeEntries(SnoopCore.store.entries.value), ContentType.Application.Json)
        }
        post("/api/clear") {
            // CSRF guard: the custom header forces a preflight this server never approves, and any
            // cross-origin caller (a hostile page open in the same browser) carries a foreign Origin.
            // Same-origin is judged against the Host actually used, which the gate above vouches for.
            val origin = call.request.header(HttpHeaders.Origin)
            val host = call.request.header(HttpHeaders.Host)
            if (call.request.header("X-Snoop") != "1" || !(origin == null || origin == "http://$host")) {
                call.respondText("forbidden", status = HttpStatusCode.Forbidden)
                return@post
            }
            SnoopCore.store.clear()
            call.respondText("ok")
        }
        sse("/api/stream") {
            // StateFlow collection is conflated by design: during the delay intermediate bursts
            // collapse and the collector resumes on the latest state, so encoding is capped at
            // ~4 frames/s and the final state is always delivered.
            SnoopCore.store.entries.collect { entries ->
                send(ServerSentEvent(data = encodeEntries(entries)))
                delay(250)
            }
        }
    }
}
