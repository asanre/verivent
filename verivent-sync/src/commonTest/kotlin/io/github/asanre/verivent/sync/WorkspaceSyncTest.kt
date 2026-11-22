package io.github.asanre.verivent.sync

import io.github.asanre.verivent.analytics.AnalyticsSink
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WorkspaceSyncTest {

    private val connection = parseConnection("https://pk:vv_key@ref.supabase.co")
    private val requests = mutableListOf<Request>()
    private val sink = AnalyticsSink()

    /** Answers device_plan with [plan] and every batch with [batches], one reply each, then 200. */
    private var plan: Reply = Reply(HttpStatusCode.OK, "null")
    private val batches = ArrayDeque<Reply>()

    private class Reply(val status: HttpStatusCode, val body: String = "0")

    private class Request(val function: String, val apiKey: String?, val body: JsonObject) {
        val events get() = body.getValue("events").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }
    }

    @BeforeTest fun setup() = Inspector.reset()

    @AfterTest fun tearDown() = Inspector.reset()

    private fun MockRequestHandleScope.answer(data: HttpRequestData): HttpResponseData {
        val function = data.url.encodedPath.substringAfterLast('/')
        val body = Json.parseToJsonElement((data.body as TextContent).text).jsonObject
        requests += Request(function, data.headers["apikey"], body)
        val reply = if (function == "device_plan") plan else batches.removeFirstOrNull() ?: Reply(HttpStatusCode.OK)
        return respond(reply.body, reply.status)
    }

    /** Starts the sync, lets its first round run and hands [block] the scope to move time on. */
    private fun syncing(block: suspend TestScope.() -> Unit) = runTest {
        // On the test's clock, so a request finishes inside the virtual time it was sent in.
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { answer(it) }
        })
        val client = HttpClient(engine)
        val job = launch { WorkspaceSync(connection, client, session = "s-1").run() }
        runCurrent()
        block()
        job.cancel()
    }

    private val sent get() = requests.filter { it.function == "ingest_events" }

    @Test
    fun sendsTheAnalyticsEventsCapturedSoFarInOneBatch() {
        sink.log("screen_view", "firebase", mapOf("screen_name" to "home"))
        Inspector.recordNetwork(NetworkTransaction(id = Inspector.nextId(), timestamp = Inspector.now(), method = "GET", url = "https://h/", host = "h", path = "/"))
        sink.log("purchase", "firebase")
        syncing {
            advanceTimeBy(6.seconds)
            val batch = sent.single()
            assertEquals(listOf("screen_view", "purchase"), batch.events)
            assertEquals("pk", batch.apiKey)
            assertEquals("vv_key", batch.body.getValue("key").jsonPrimitive.content)
            assertEquals("s-1", batch.body.getValue("session").jsonPrimitive.content)
            assertTrue(batch.body.getValue("library_version").jsonPrimitive.content.isNotEmpty())
            assertTrue(batch.body.getValue("platform").jsonPrimitive.content.isNotEmpty())
        }
    }

    @Test
    fun sendsOnlyWhatArrivedSinceTheLastBatch() {
        sink.log("screen_view", "firebase")
        syncing {
            advanceTimeBy(6.seconds)
            sink.log("purchase", "firebase")
            advanceTimeBy(6.seconds)
            assertEquals(listOf(listOf("screen_view"), listOf("purchase")), sent.map { it.events })
            advanceTimeBy(30.seconds)
            assertEquals(2, sent.size)
        }
    }

    @Test
    fun splitsWhatIsPendingIntoBatchesOf200() {
        Inspector.configure(capacity = 1000)
        repeat(450) { sink.log("e$it", "firebase") }
        syncing {
            advanceTimeBy(6.seconds)
            assertEquals(listOf(200, 200, 50), sent.map { it.events.size })
        }
    }

    @Test
    fun sendsTheSameEventsAgainWhenTheServerIsUnreachable() {
        sink.log("screen_view", "firebase")
        batches += Reply(HttpStatusCode.ServiceUnavailable)
        syncing {
            advanceTimeBy(6.seconds)
            assertEquals(1, sent.size)
            advanceTimeBy(11.seconds)
            assertEquals(listOf(listOf("screen_view"), listOf("screen_view")), sent.map { it.events })
        }
    }

    @Test
    fun waitsLongerAfterEachFailureUpToFiveMinutes() {
        sink.log("screen_view", "firebase")
        repeat(20) { batches += Reply(HttpStatusCode.InternalServerError) }
        syncing {
            advanceTimeBy(60.minutes)
            // Sent at 5 s, then after 10, 20, 40, 80 and 160 s, then every 300 s.
            assertEquals(6 + (3600 - 315) / 300, sent.size)
        }
    }

    @Test
    fun stopsForGoodWhenTheKeyIsRevoked() {
        sink.log("screen_view", "firebase")
        batches += Reply(HttpStatusCode.Unauthorized)
        syncing {
            advanceTimeBy(6.seconds)
            sink.log("purchase", "firebase")
            advanceTimeBy(30.minutes)
            assertEquals(1, sent.size)
        }
    }

    @Test
    fun dropsABatchTheServerRefusesAndGoesOn() {
        sink.log("screen_view", "firebase")
        batches += Reply(HttpStatusCode.BadRequest)
        syncing {
            advanceTimeBy(6.seconds)
            sink.log("purchase", "firebase")
            advanceTimeBy(6.seconds)
            assertEquals(listOf(listOf("screen_view"), listOf("purchase")), sent.map { it.events })
        }
    }

    @Test
    fun loadsTheWorkspacesPublishedPlanFirst() {
        plan = Reply(HttpStatusCode.OK, """{ "version": "2.0.0", "events": [] }""")
        syncing {
            assertEquals("device_plan", requests.first().function)
            assertEquals("vv_key", requests.first().body.getValue("key").jsonPrimitive.content)
            assertEquals("2.0.0", Inspector.trackingPlan.value?.version)
        }
    }

    @Test
    fun aWorkspaceWithNothingPublishedLeavesThePlanAlone() {
        syncing { assertNull(Inspector.trackingPlan.value) }
    }

    @Test
    fun aPlanTheHostLoadedIsKept() {
        Inspector.loadTrackingPlan("""{ "version": "1.0.0", "events": [] }""")
        plan = Reply(HttpStatusCode.OK, """{ "version": "2.0.0", "events": [] }""")
        syncing { assertEquals("1.0.0", Inspector.trackingPlan.value?.version) }
    }

    @Test
    fun asksForThePlanAgainUntilItGetsAnAnswer() {
        plan = Reply(HttpStatusCode.ServiceUnavailable)
        sink.log("screen_view", "firebase")
        syncing {
            plan = Reply(HttpStatusCode.OK, """{ "version": "2.0.0", "events": [] }""")
            advanceTimeBy(11.seconds)
            assertEquals("2.0.0", Inspector.trackingPlan.value?.version)
            advanceTimeBy(6.seconds)
            assertEquals(listOf("device_plan", "device_plan", "ingest_events"), requests.map { it.function })
        }
    }
}
