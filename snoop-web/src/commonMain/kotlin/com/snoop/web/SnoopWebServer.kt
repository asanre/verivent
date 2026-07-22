package com.snoop.web

import com.snoop.core.SnoopCore
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent

/**
 * A tiny embedded HTTP server that serves the Snoop timeline as a live web page, so you can inspect
 * traffic from a desktop browser while debugging from Android Studio (`adb forward tcp:9394`).
 *
 * Reads the same shared [SnoopCore] store the on-device UI uses. Android-only in v1.
 */
object SnoopWebServer {

    private var server: EmbeddedServer<*, *>? = null
    private var port: Int = DEFAULT_PORT

    val isRunning: Boolean get() = server != null

    val url: String get() = "http://localhost:$port"

    /** Start the server (idempotent). Returns the local URL. */
    fun start(port: Int = DEFAULT_PORT): String {
        if (server != null) return url
        this.port = port
        server = embeddedServer(CIO, port = port) { module() }.apply { start(wait = false) }
        return url
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 300, timeoutMillis = 1000)
        server = null
    }

    private fun Application.module() {
        install(SSE)
        routing {
            get("/") { call.respondText(INDEX_HTML, ContentType.Text.Html) }
            get("/api/entries") {
                call.respondText(encodeEntries(SnoopCore.store.entries.value), ContentType.Application.Json)
            }
            sse("/api/stream") {
                SnoopCore.store.entries.collect { entries ->
                    send(ServerSentEvent(data = encodeEntries(entries)))
                }
            }
        }
    }

    const val DEFAULT_PORT: Int = 9394
}
