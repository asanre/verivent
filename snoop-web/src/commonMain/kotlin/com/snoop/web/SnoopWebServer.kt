package com.snoop.web

import com.snoop.core.SnoopCore
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
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
 * from your Mac over Wi-Fi via [lanUrl], optionally gated by a [token]. Reads the same shared
 * [SnoopCore] store the on-device UI uses.
 */
object SnoopWebServer {

    private val server = AtomicReference<EmbeddedServer<*, *>?>(null)
    private var port: Int = DEFAULT_PORT
    private var boundHost: String = LOOPBACK
    private var activeToken: String? = null

    val isRunning: Boolean get() = server.load() != null

    val url: String get() = "http://localhost:$port"

    /** The access token in effect, or null when the server is open (the default). */
    val token: String? get() = activeToken

    /** A human label for this device (e.g. "iPhone de Toni"), to tell devices apart on a shared network. */
    val deviceLabel: String get() = localDeviceName()

    /**
     * The URL to reach this device from another machine on the LAN, or null when bound to loopback
     * (nothing is listening off-device) or when no LAN address is available. Includes the token when set.
     */
    val lanUrl: String? get() {
        if (boundHost == LOOPBACK) return null
        val ip = localHostAddress() ?: return null
        val base = "http://$ip:$port"
        return activeToken?.let { "$base/?t=$it" } ?: base
    }

    /**
     * Start the server (idempotent, thread-safe). Returns the local URL.
     *
     * @param host bind address. Defaults to loopback; pass `"0.0.0.0"` to reach it from another
     *   machine on the LAN (see [lanUrl]).
     * @param token when non-null, every request must carry a matching `?t=` query parameter. Opt-in:
     *   the server is open by default, which is fine on a trusted network.
     */
    fun start(port: Int = DEFAULT_PORT, host: String = LOOPBACK, token: String? = null): String {
        val candidate = runCatching { createServer(port, host, token) }.getOrNull() ?: return url
        if (!server.compareAndSet(null, candidate)) return url
        this.port = port
        this.boundHost = host
        this.activeToken = token
        // Bind failure (port in use, restricted env) must never crash the host app.
        runCatching { candidate.start(wait = false) }
            .onFailure { server.compareAndSet(candidate, null) }
        return url
    }

    fun stop() {
        val running = server.exchange(null) ?: return
        activeToken = null
        boundHost = LOOPBACK
        running.stop(gracePeriodMillis = 300, timeoutMillis = 1000)
    }

    internal fun createServer(port: Int, host: String = LOOPBACK, token: String? = null): EmbeddedServer<*, *> =
        embeddedServer(CIO, host = host, port = port) { snoopModule(port, token) }

    const val DEFAULT_PORT: Int = 9394
    internal const val LOOPBACK: String = "127.0.0.1"
}

internal fun Application.snoopModule(port: Int = SnoopWebServer.DEFAULT_PORT, token: String? = null) {
    install(SSE)
    // Token gate is opt-in: when no token is configured every request passes, as before.
    fun ApplicationCall.authorized(): Boolean =
        token == null || request.queryParameters["t"] == token
    routing {
        get("/") {
            if (!call.authorized()) return@get call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            call.respondText(INDEX_HTML, ContentType.Text.Html)
        }
        get("/api/entries") {
            if (!call.authorized()) return@get call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            call.respondText(encodeEntries(SnoopCore.store.entries.value), ContentType.Application.Json)
        }
        post("/api/clear") {
            if (!call.authorized()) return@post call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            // CSRF guard: the custom header forces a preflight this server never approves, and any
            // cross-origin caller (a hostile page open in the same browser) carries a foreign Origin.
            // Same-origin is judged against the Host actually used, so it holds for any bind address.
            val origin = call.request.header(HttpHeaders.Origin)
            val host = call.request.header(HttpHeaders.Host)
            val originAllowed = origin == null ||
                (host != null && origin == "http://$host") ||
                origin == "http://localhost:$port" ||
                origin == "http://127.0.0.1:$port"
            if (call.request.header("X-Snoop") != "1" || !originAllowed) {
                call.respondText("forbidden", status = HttpStatusCode.Forbidden)
                return@post
            }
            SnoopCore.store.clear()
            call.respondText("ok")
        }
        sse("/api/stream") {
            if (!call.authorized()) return@sse
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
