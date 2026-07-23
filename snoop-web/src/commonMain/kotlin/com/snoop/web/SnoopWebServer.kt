package com.snoop.web

import com.snoop.core.SnoopCore
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
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
 * traffic from a desktop browser while debugging from Android Studio (`adb forward tcp:9394`).
 *
 * Binds loopback only — the page is never reachable from other devices on the LAN.
 * Reads the same shared [SnoopCore] store the on-device UI uses. Android-only in v1.
 */
object SnoopWebServer {

    private val server = AtomicReference<EmbeddedServer<*, *>?>(null)
    private var port: Int = DEFAULT_PORT

    val isRunning: Boolean get() = server.load() != null

    val url: String get() = "http://localhost:$port"

    /** Start the server (idempotent, thread-safe). Returns the local URL. */
    fun start(port: Int = DEFAULT_PORT): String {
        val candidate = runCatching { createServer(port) }.getOrNull() ?: return url
        if (!server.compareAndSet(null, candidate)) return url
        this.port = port
        // Bind failure (port in use, restricted env) must never crash the host app.
        runCatching { candidate.start(wait = false) }
            .onFailure { server.compareAndSet(candidate, null) }
        return url
    }

    fun stop() {
        val running = server.exchange(null) ?: return
        running.stop(gracePeriodMillis = 300, timeoutMillis = 1000)
    }

    internal fun createServer(port: Int): EmbeddedServer<*, *> =
        embeddedServer(CIO, host = "127.0.0.1", port = port) { snoopModule(port) }

    const val DEFAULT_PORT: Int = 9394
}

internal fun Application.snoopModule(port: Int = SnoopWebServer.DEFAULT_PORT) {
    install(SSE)
    routing {
        get("/") { call.respondText(INDEX_HTML, ContentType.Text.Html) }
        get("/api/entries") {
            call.respondText(encodeEntries(SnoopCore.store.entries.value), ContentType.Application.Json)
        }
        post("/api/clear") {
            // CSRF guard: the custom header forces a preflight this server never approves, and any
            // cross-origin caller (a hostile page open in the same browser) carries a foreign Origin.
            val origin = call.request.header(HttpHeaders.Origin)
            val originAllowed =
                origin == null || origin == "http://localhost:$port" || origin == "http://127.0.0.1:$port"
            if (call.request.header("X-Snoop") != "1" || !originAllowed) {
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
