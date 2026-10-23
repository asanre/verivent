package io.github.asanre.verivent.web

import io.github.asanre.verivent.analytics.AnalyticsSink
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class WebViewerTest {

    @AfterTest
    fun tearDown() {
        WebViewer.stop()
        Inspector.reset()
    }

    private fun seedEntry() {
        Inspector.recordAnalytics(AnalyticsEntry("1", Instant.fromEpochMilliseconds(0), "event", "firebase"))
    }

    @Test
    fun bindsLoopbackOnly() {
        val server = WebViewer.createServer(9377)
        assertEquals("127.0.0.1", server.engineConfig.connectors.single().host)
    }

    @Test
    fun clearWithoutHeaderIsRejected() = testApplication {
        seedEntry()
        application { webViewerModule() }
        val response = client.post("/api/clear")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(1, Inspector.store.entries.value.size)
    }

    @Test
    fun clearWithForeignOriginIsRejected() = testApplication {
        seedEntry()
        application { webViewerModule() }
        val response = client.post("/api/clear") {
            header("X-Verivent", "1")
            header(HttpHeaders.Origin, "http://evil.example")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(1, Inspector.store.entries.value.size)
    }

    @Test
    fun clearWithHeaderAndLocalOriginClears() = testApplication {
        seedEntry()
        application { webViewerModule() }
        val response = client.post("/api/clear") {
            header("X-Verivent", "1")
            header(HttpHeaders.Host, "localhost:${WebViewer.DEFAULT_PORT}")
            header(HttpHeaders.Origin, "http://localhost:${WebViewer.DEFAULT_PORT}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(Inspector.store.entries.value.isEmpty())
    }

    @Test
    fun clearWithLanSameOriginClears() = testApplication {
        seedEntry()
        application { webViewerModule() }
        val response = client.post("/api/clear") {
            header("X-Verivent", "1")
            header(HttpHeaders.Host, "192.168.1.10:9394")
            header(HttpHeaders.Origin, "http://192.168.1.10:9394")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(Inspector.store.entries.value.isEmpty())
    }

    private val plan = """{ "version": "4.2.0", "events": [ { "name": "search", "channel": "firebase", "schema": {} } ] }"""

    @Test
    fun loadingAPlanNeedsTheSameGuardAsClear() = testApplication {
        application { webViewerModule() }
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/tracking-plan") { setBody(plan) }.status)
        val foreign = client.post("/api/tracking-plan") {
            header("X-Verivent", "1")
            header(HttpHeaders.Origin, "http://evil.example")
            setBody(plan)
        }
        assertEquals(HttpStatusCode.Forbidden, foreign.status)
        assertNull(Inspector.trackingPlan.value)
    }

    @Test
    fun aValidPlanLoadsAndReportsItsVersionAndEventsAndClearRemovesIt() = testApplication {
        application { webViewerModule() }
        val loaded = client.post("/api/tracking-plan") {
            header("X-Verivent", "1")
            setBody(plan)
        }
        assertEquals(HttpStatusCode.OK, loaded.status)
        assertTrue("4.2.0" in loaded.bodyAsText())
        val status = Json.parseToJsonElement(client.get("/api/tracking-plan").bodyAsText()).jsonObject
        assertEquals("4.2.0", status.getValue("version").jsonPrimitive.content)
        val event = status.getValue("events").jsonArray.single().jsonObject
        assertEquals("search", event.getValue("name").jsonPrimitive.content)
        assertEquals("firebase", event.getValue("channel").jsonPrimitive.content)

        client.post("/api/tracking-plan/clear") { header("X-Verivent", "1") }
        assertNull(Inspector.trackingPlan.value)
    }

    @Test
    fun anInvalidPlanIsRejectedWithTheReason() = testApplication {
        application { webViewerModule() }
        val response = client.post("/api/tracking-plan") {
            header("X-Verivent", "1")
            setBody("""{ "version": "1" }""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue("events" in response.bodyAsText())
    }

    @Test
    fun anOversizedPlanIsRejected() = testApplication {
        application { webViewerModule() }
        val response = client.post("/api/tracking-plan") {
            header("X-Verivent", "1")
            setBody("x".repeat(2 * 1024 * 1024 + 1))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test
    fun aPlanWithoutContentLengthIsLimitedByBytesNotCharacters() = testApplication {
        application { webViewerModule() }
        // Two UTF-8 bytes per char: over the limit in bytes, well under it in chars.
        val bytes = "é".repeat(1024 * 1024 + 1).encodeToByteArray()
        val response = client.post("/api/tracking-plan") {
            header("X-Verivent", "1")
            setBody(object : OutgoingContent.WriteChannelContent() {
                override suspend fun writeTo(channel: ByteWriteChannel) = channel.writeFully(bytes)
            })
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test
    fun aMutationIsRefusedBeforeRoutingEvenOnAnUnknownRoute() = testApplication {
        application { webViewerModule() }
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/anything").status)
    }

    @Test
    fun entriesOpenWhenNoTokenConfigured() = testApplication {
        application { webViewerModule() }
        assertEquals(HttpStatusCode.OK, client.get("/api/entries").status)
    }

    @Test
    fun entriesRejectedWithoutOrWithWrongToken() = testApplication {
        application { webViewerModule(token = "secret") }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/entries").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/entries?t=nope").status)
    }

    @Test
    fun entriesAllowedWithMatchingToken() = testApplication {
        application { webViewerModule(token = "secret") }
        assertEquals(HttpStatusCode.OK, client.get("/api/entries?t=secret").status)
    }

    @Test
    fun sseStreamConflatesBursts() = testApplication {
        application { webViewerModule() }
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
                Inspector.recordAnalytics(
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
    fun sseSecondFrameCarriesOnlyTheDelta() = testApplication {
        seedEntry()
        application { webViewerModule() }
        val frames = mutableListOf<String>()
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
            Inspector.recordAnalytics(AnalyticsEntry("2", Instant.fromEpochMilliseconds(1), "second", "ch"))
            withTimeout(10_000) { while (frames.size < 2) delay(20) }
            reader.cancel()
        }
        assertTrue("\"event\"" in frames.first(), "the first frame carries everything: ${frames.first()}")
        val delta = frames[1]
        assertTrue("\"second\"" in delta, "the new entry belongs in the delta: $delta")
        assertFalse("\"event\"" in delta, "an unchanged entry must not be re-sent: $delta")
        assertTrue("[\"1\",\"2\"]" in delta, "every id stays in the frame so the client can prune: $delta")
    }

    @Test
    fun sseResendsAnEntryWhenItChanges() = testApplication {
        seedEntry()
        application { webViewerModule() }
        val frames = mutableListOf<String>()
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
            Inspector.store.update("1") { (it as AnalyticsEntry).copy(name = "renamed") }
            withTimeout(10_000) { while (frames.size < 2) delay(20) }
            reader.cancel()
        }
        val delta = frames[1]
        assertTrue("\"renamed\"" in delta, "an updated entry must be re-sent: $delta")
        assertTrue("[\"1\"]" in delta, "the id keeps its position: $delta")
    }

    @Test
    fun concurrentStartYieldsOneServer() = runTest {
        val port = 9378
        val urls = (1..8).map { async(Dispatchers.Default) { WebViewer.start(port = port) } }.awaitAll()
        val token = assertNotNull(WebViewer.token)
        assertTrue(urls.all { it == "http://localhost:$port/?t=$token" }, "got $urls")
        assertTrue(WebViewer.isRunning)
    }

    @Test
    fun startOnATakenPortLeavesTheServerStopped() = runTest {
        val port = 9377
        val squatter = WebViewer.createServer(port)
        squatter.start(wait = false)
        try {
            // CIO binds on a coroutine, so the EADDRINUSE never reaches start()'s caller. Unhandled
            // it reaches the default handler, which on Kotlin/Native takes the host process down.
            withContext(Dispatchers.Default) { delay(300) }
            WebViewer.start(port = port)
            withContext(Dispatchers.Default) { delay(500) }
            assertFalse(WebViewer.isRunning, "a failed bind must not leave the server marked running")
        } finally {
            squatter.stop(gracePeriodMillis = 0, timeoutMillis = 0)
        }
    }

    @Test
    fun startGatesLocalBindsToo() = runTest {
        val url = WebViewer.start(WebViewerBind.Local, port = 9379)
        val token = assertNotNull(WebViewer.token, "loopback must be gated as well")
        assertEquals("http://localhost:9379/?t=$token", url)
    }

    @Test
    fun stopThenStartOnSamePortWorks() = runTest {
        val port = 9380
        WebViewer.start(port = port)
        WebViewer.stop()
        assertFalse(WebViewer.isRunning, "stop() must not return before the engine is down")
        assertTrue(WebViewer.start(port = port).endsWith("t=${WebViewer.token}"))
        assertTrue(WebViewer.isRunning)
    }

    @Test
    fun stopThenStartOnSamePortWorksWithAStreamOpen() = runTest {
        val port = 9381
        withContext(Dispatchers.Default) {
            HttpClient(CIO).use { client ->
                // Twice: the first round sets the trap, the second one walks into it.
                repeat(2) { round ->
                    // The URL carries the token, and survives a start that fails after returning.
                    val url = WebViewer.start(port = port)
                    assertTrue(serves(client, url), "round $round: the viewer never served a response")
                    // The viewer page holds an SSE stream open for as long as it is on screen, so
                    // stop() closes a live connection rather than an idle one.
                    val opened = CompletableDeferred<Unit>()
                    val stream = launch { runCatching { readStream(client, url, opened) } }
                    withTimeout(10_000) { opened.await() }
                    WebViewer.stop()
                    stream.cancelAndJoin()
                }
            }
        }
    }

    /** The bind is asynchronous, so a served response is the only trustworthy readiness signal. */
    private suspend fun serves(client: HttpClient, url: String): Boolean {
        repeat(50) {
            val body = runCatching { client.get(url.replace("/?t=", "/api/entries?t=")).bodyAsText() }
            if (body.getOrNull()?.startsWith("[") == true) return true
            delay(100)
        }
        return false
    }

    /** Completes [opened] on the first frame and keeps reading, so the socket stays live. */
    private suspend fun readStream(client: HttpClient, url: String, opened: CompletableDeferred<Unit>) {
        client.prepareGet(url.replace("/?t=", "/api/stream?t=")).execute { response ->
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data:")) opened.complete(Unit)
            }
        }
    }

    @Test
    fun streamWithoutTokenIsRejectedOutright() = testApplication {
        application { webViewerModule(token = "secret") }
        // A silently-closed stream would leave EventSource reconnecting forever instead of failing.
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/stream").status)
    }

    @Test
    fun everyRouteIsBehindTheSameGate() = testApplication {
        application { webViewerModule(token = "secret") }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/entries").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/export").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/clear").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/assert/all").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/assert/bad").status)
    }

    @Test
    fun theDownloadIsTheExportFileWithOnlyTheEvents() = testApplication {
        seedEntry()
        Inspector.recordNetwork(NetworkTransaction("2", Instant.fromEpochMilliseconds(1), "GET", "https://h/a", "h", "/a"))
        application { webViewerModule(token = "secret") }

        val file = Json.parseToJsonElement(client.get("/api/export?t=secret").bodyAsText()).jsonArray
        assertEquals(listOf("source", "analytics"), file.map { it.jsonObject.getValue("type").jsonPrimitive.content })
    }

    @Test
    fun thePageCarriesTheConfiguredTitleEscaped() = testApplication {
        application { webViewerModule() }
        assertTrue("<title>Verivent</title>" in client.get("/").bodyAsText())

        Inspector.configure(title = "Patata <script>alert(1)</script>")
        val page = client.get("/").bodyAsText()
        assertTrue("Patata &lt;script&gt;alert(1)&lt;/script&gt;" in page)
        assertTrue("<script>alert(1)</script>" !in page, "host title must not reach the page as markup")
        assertTrue("@VERIVENT_TITLE@" !in page, "every placeholder must be substituted")
    }

    @Test
    fun foreignHostHeaderIsRejected() = testApplication {
        seedEntry()
        application { webViewerModule() }
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
        assertFalse(hostHeaderAllowed("verivent.local"))
    }

    @Test
    fun everyBindGeneratesATokenUnlessOneIsForced() {
        assertEquals("mine", resolveToken("mine"))

        val generated = resolveToken(null)
        assertTrue(generated.length >= 16, "expected a long token, got '$generated'")
        assertNotEquals(generated, resolveToken(null))
    }

    @Test
    fun aBlankForcedTokenNeverOpensTheGate() = testApplication {
        val token = resolveToken(" ")
        assertTrue(token.isNotBlank() && token.length >= 16, "expected a generated token, got '$token'")
        application { webViewerModule(token) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/entries").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/entries?t=").status)
    }

    @Test
    fun bindAddressMapsEachMode() {
        assertEquals("127.0.0.1", WebViewerBind.Local.bindAddress)
        assertEquals("0.0.0.0", WebViewerBind.Lan.bindAddress)
        assertEquals("192.168.1.5", WebViewerBind.Host("192.168.1.5").bindAddress)
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
        assertEquals("http://localhost:${WebViewer.DEFAULT_PORT}", urlOf(null))
        assertNull(lanUrlOf(null, "192.168.1.10"))
        assertNull(lanUrlOf(running(WebViewerBind.Local), "192.168.1.10"))
        assertNull(lanUrlOf(running(WebViewerBind.Lan), null))
        assertEquals("http://192.168.1.10:9394/?t=abc", lanUrlOf(running(WebViewerBind.Lan), "192.168.1.10"))
    }

    @Test
    fun localUrlCarriesTheToken() {
        assertEquals("http://localhost:9394/?t=abc", urlOf(running(WebViewerBind.Local)))
        assertEquals("http://localhost:9394/?t=abc", urlOf(running(WebViewerBind.Lan)))
    }

    private fun running(bind: WebViewerBind, token: String = "abc"): Running {
        val scope = CoroutineScope(SupervisorJob())
        return Running(
            server = WebViewer.createServer(9394, bind.bindAddress, token, scope),
            scope = scope,
            port = 9394,
            bind = bind,
            token = token,
        )
    }

    private val assertPlan = """
        { "version": "3.0.0", "events": [ { "name": "search", "channel": "firebase",
          "schema": { "required": ["search_term"], "properties": { "search_term": { "type": "string" } } } } ] }
    """

    @Test
    fun aTestThatOnlySpeaksHttpKnowsWhetherAnEventBrokeThePlan() = testApplication {
        application { webViewerModule(token = "t0k") }
        client.post("/api/tracking-plan?t=t0k") {
            header("X-Verivent", "1")
            setBody(assertPlan)
        }
        val sink = AnalyticsSink()
        sink.log("search", "firebase", mapOf("search_term" to "shoes"))
        sink.log("search", "firebase", mapOf("search_term" to 7))
        sink.log("screen_view", "firebase")

        val summary = Json.parseToJsonElement(client.get("/api/assert/all?t=t0k").bodyAsText()).jsonObject
        assertEquals(
            mapOf("plan" to "3.0.0", "total" to "3", "good" to "1", "bad" to "1", "unplanned" to "1"),
            summary.mapValues { it.value.jsonPrimitive.content },
        )
        val bad = Json.parseToJsonElement(client.get("/api/assert/bad?t=t0k").bodyAsText()).jsonArray.single().jsonObject
        assertEquals("search_term", bad.getValue("issues").jsonArray.single().jsonObject.getValue("property").jsonPrimitive.content)

        client.post("/api/clear?t=t0k") { header("X-Verivent", "1") }
        val afterReset = Json.parseToJsonElement(client.get("/api/assert/all?t=t0k").bodyAsText()).jsonObject
        assertEquals("0", afterReset.getValue("total").jsonPrimitive.content)
    }

    @Test
    fun assertionsFilterByEventAndRejectUnknownVerdicts() = testApplication {
        application { webViewerModule() }
        Inspector.loadTrackingPlan(assertPlan)
        val sink = AnalyticsSink()
        sink.log("search", "firebase", mapOf("search_term" to "a"))
        sink.log("search", "server-side")
        sink.log("login", "firebase")

        suspend fun names(path: String) = Json.parseToJsonElement(client.get(path).bodyAsText()).jsonArray.map {
            it.jsonObject.getValue("name").jsonPrimitive.content + "/" + it.jsonObject.getValue("channel").jsonPrimitive.content
        }
        assertEquals(listOf("search/server-side", "login/firebase"), names("/api/assert/unplanned"))
        assertEquals(listOf("login/firebase"), names("/api/assert/unplanned?name=login"))
        assertEquals(listOf("search/server-side"), names("/api/assert/unplanned?channel=server-side"))
        assertEquals(listOf("search/firebase"), names("/api/assert/good"))
        assertEquals(HttpStatusCode.NotFound, client.get("/api/assert/maybe").status)
    }

    @Test
    fun withoutAPlanEveryEventIsUnplanned() = testApplication {
        application { webViewerModule() }
        AnalyticsSink().log("search", "firebase")
        val summary = Json.parseToJsonElement(client.get("/api/assert/all").bodyAsText()).jsonObject
        assertEquals(
            mapOf("plan" to "null", "total" to "1", "good" to "0", "bad" to "0", "unplanned" to "1"),
            summary.mapValues { it.value.jsonPrimitive.content },
        )
    }
}
