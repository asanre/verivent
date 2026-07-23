package com.snoop.web

import com.snoop.core.SnoopCore
import com.snoop.core.model.AnalyticsEntry
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class SnoopWebServerTest {

    @AfterTest
    fun tearDown() {
        SnoopWebServer.stop()
        SnoopCore.reset()
    }

    private fun seedEntry() {
        SnoopCore.recordAnalytics(AnalyticsEntry("1", Instant.fromEpochMilliseconds(0), "event", "firebase"))
    }

    @Test
    fun bindsLoopbackOnly() {
        val server = SnoopWebServer.createServer(9377)
        assertEquals("127.0.0.1", server.engineConfig.connectors.single().host)
    }

    @Test
    fun clearWithoutHeaderIsRejected() = testApplication {
        seedEntry()
        application { snoopModule() }
        val response = client.post("/api/clear")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(1, SnoopCore.store.entries.value.size)
    }

    @Test
    fun clearWithForeignOriginIsRejected() = testApplication {
        seedEntry()
        application { snoopModule() }
        val response = client.post("/api/clear") {
            header("X-Snoop", "1")
            header(HttpHeaders.Origin, "http://evil.example")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(1, SnoopCore.store.entries.value.size)
    }

    @Test
    fun clearWithHeaderAndLocalOriginClears() = testApplication {
        seedEntry()
        application { snoopModule() }
        val response = client.post("/api/clear") {
            header("X-Snoop", "1")
            header(HttpHeaders.Origin, "http://localhost:${SnoopWebServer.DEFAULT_PORT}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(SnoopCore.store.entries.value.isEmpty())
    }

    @Test
    fun sseStreamConflatesBursts() = testApplication {
        application { snoopModule() }
        val frames = mutableListOf<String>()
        // Single-threaded dispatcher: real time (no virtual-clock skew) and confined list access.
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val reader = launch {
                client.prepareGet("/api/stream").execute { response ->
                    val channel = response.bodyAsChannel()
                    while (true) {
                        val line = channel.readUTF8Line() ?: break
                        if (line.startsWith("data:")) frames += line
                    }
                }
            }
            withTimeout(10_000) { while (frames.isEmpty()) delay(20) }
            repeat(50) { n ->
                SnoopCore.recordAnalytics(
                    AnalyticsEntry("e$n", Instant.fromEpochMilliseconds(n.toLong()), "ev$n", "ch"),
                )
            }
            // The throttled stream must still converge on the final state.
            withTimeout(10_000) { while (frames.none { "\"ev49\"" in it }) delay(50) }
            reader.cancel()
        }
        assertTrue(frames.size < 25, "expected far fewer frames than 50 mutations, got ${frames.size}")
    }

    @Test
    fun concurrentStartYieldsOneServer() = runTest {
        val port = 9378
        val urls = (1..8).map { async(Dispatchers.Default) { SnoopWebServer.start(port) } }.awaitAll()
        assertTrue(urls.all { it == "http://localhost:$port" })
        assertTrue(SnoopWebServer.isRunning)
    }
}
