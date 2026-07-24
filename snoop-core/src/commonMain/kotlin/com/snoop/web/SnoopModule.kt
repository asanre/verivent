package com.snoop.web

import com.snoop.core.SnoopCore
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.delay

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
