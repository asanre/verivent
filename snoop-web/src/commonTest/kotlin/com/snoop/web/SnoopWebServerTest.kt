package com.snoop.web

import com.snoop.core.SnoopCore
import com.snoop.core.model.AnalyticsEntry
import io.ktor.client.request.get
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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
            header(HttpHeaders.Host, "localhost:${SnoopWebServer.DEFAULT_PORT}")
            header(HttpHeaders.Origin, "http://localhost:${SnoopWebServer.DEFAULT_PORT}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(SnoopCore.store.entries.value.isEmpty())
    }

    @Test
    fun clearWithLanSameOriginClears() = testApplication {
        seedEntry()
        application { snoopModule() }
        val response = client.post("/api/clear") {
            header("X-Snoop", "1")
            header(HttpHeaders.Host, "192.168.1.10:9394")
            header(HttpHeaders.Origin, "http://192.168.1.10:9394")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(SnoopCore.store.entries.value.isEmpty())
    }

    @Test
    fun entriesOpenWhenNoTokenConfigured() = testApplication {
        application { snoopModule() }
        assertEquals(HttpStatusCode.OK, client.get("/api/entries").status)
    }

    @Test
    fun entriesRejectedWithoutOrWithWrongToken() = testApplication {
        application { snoopModule(token = "secret") }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/entries").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/entries?t=nope").status)
    }

    @Test
    fun entriesAllowedWithMatchingToken() = testApplication {
        application { snoopModule(token = "secret") }
        assertEquals(HttpStatusCode.OK, client.get("/api/entries?t=secret").status)
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

    @Test
    fun streamWithoutTokenIsRejectedOutright() = testApplication {
        application { snoopModule(token = "secret") }
        // A silently-closed stream would leave EventSource reconnecting forever instead of failing.
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/stream").status)
    }

    @Test
    fun everyRouteIsBehindTheSameGate() = testApplication {
        application { snoopModule(token = "secret") }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/entries").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/clear").status)
    }

    @Test
    fun foreignHostHeaderIsRejected() = testApplication {
        seedEntry()
        application { snoopModule() }
        val response = client.get("/api/entries") { header(HttpHeaders.Host, "evil.example:9394") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun literalAddressHostsAreAccepted() {
        assertTrue(hostHeaderAllowed("localhost:9394"))
        assertTrue(hostHeaderAllowed("127.0.0.1:9394"))
        assertTrue(hostHeaderAllowed("192.168.1.10:9394"))
        assertTrue(hostHeaderAllowed("[::1]:9394"))
        assertTrue(hostHeaderAllowed(null))
        assertFalse(hostHeaderAllowed("evil.example:9394"))
        assertFalse(hostHeaderAllowed("snoop.local"))
    }

    @Test
    fun loopbackStaysOpenButLanBindsGenerateAToken() {
        assertNull(effectiveToken(SnoopWebServer.LOOPBACK, null))
        assertEquals("mine", effectiveToken(SnoopWebServer.LOOPBACK, "mine"))
        assertEquals("mine", effectiveToken("0.0.0.0", "mine"))

        val generated = effectiveToken("0.0.0.0", null)
        assertNotNull(generated)
        assertTrue(generated.length >= 16, "expected a long token, got '$generated'")
        assertNotEquals(generated, effectiveToken("0.0.0.0", null))
    }

    @Test
    fun tokenCompareIsExactAndLengthSafe() {
        assertTrue(tokensEqual("secret", "secret"))
        assertFalse(tokensEqual("secret", ""))
        assertFalse(tokensEqual("secret", "secre"))
        assertFalse(tokensEqual("secret", "secretx"))
        assertFalse(tokensEqual("secret", "Secret"))
        assertTrue(tokensEqual("", ""))
    }

    @Test
    fun derivedUrlsFallBackWhenNotRunning() {
        assertEquals("http://localhost:${SnoopWebServer.DEFAULT_PORT}", urlOf(null))
        assertNull(lanUrlOf(null, "192.168.1.10"))
        assertNull(lanUrlOf(running(host = SnoopWebServer.LOOPBACK), "192.168.1.10"))
        assertNull(lanUrlOf(running(host = "0.0.0.0"), null))
        assertEquals("http://192.168.1.10:9394/?t=abc", lanUrlOf(running(host = "0.0.0.0"), "192.168.1.10"))
    }

    private fun running(host: String) = Running(
        server = SnoopWebServer.createServer(9394, host, "abc"),
        port = 9394,
        host = host,
        token = "abc",
    )
}
