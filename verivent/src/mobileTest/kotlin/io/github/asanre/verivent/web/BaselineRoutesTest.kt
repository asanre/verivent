package io.github.asanre.verivent.web

import io.github.asanre.verivent.analytics.AnalyticsSink
import io.github.asanre.verivent.core.Inspector
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaselineRoutesTest {

    @AfterTest fun tearDown() = Inspector.reset()

    private val sink = AnalyticsSink()

    private fun purchase(value: Any = 9.5, order: String = "T-1") =
        sink.log("purchase", "firebase", mapOf("value" to value, "transaction_id" to order))

    private val steps = """
        { "flow": "checkout", "steps": [
          { "name": "browse", "events": [ { "name": "screen_view", "channel": "firebase", "schema": {} } ] },
          { "name": "pay", "events": [ { "name": "purchase", "channel": "firebase",
            "schema": { "properties": { "value": { "const": 9.5 }, "transaction_id": { "const": "{{orderId}}" } } } } ] } ] }
    """

    private suspend fun HttpClient.call(path: String, body: String = "") = post("$path${if ('?' in path) '&' else '?'}t=k") {
        header("X-Verivent", "1")
        setBody(body)
    }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.passed(): Boolean = json().getValue("passed").jsonPrimitive.boolean

    @Test
    fun aFlowIsCheckedStepByStepOverHttp() = testApplication {
        application { webViewerModule(token = "k") }
        assertEquals(HttpStatusCode.OK, client.call("/api/baseline/flow?timeout=100", steps).status)

        client.call("/api/baseline/step?name=browse")
        sink.log("screen_view", "firebase")
        assertTrue(client.call("/api/baseline/step/end").passed())

        client.call("/api/baseline/step?name=pay")
        purchase(order = "T-42")
        assertTrue(client.call("/api/baseline/step/end", """{ "variables": { "orderId": "T-42" } }""").passed())
        assertTrue(client.call("/api/baseline/finish").passed())
    }

    @Test
    fun aMismatchAnswersWithTheReportTheKotlinTestWouldThrow() = testApplication {
        application { webViewerModule(token = "k") }
        client.call("/api/baseline/flow?timeout=100", steps)
        client.call("/api/baseline/step?name=browse")
        sink.log("screen_view", "firebase")
        client.call("/api/baseline/step/end")
        client.call("/api/baseline/step?name=pay")
        purchase(value = 12, order = "T-42")

        val result = client.call("/api/baseline/step/end", """{ "variables": { "orderId": "T-42" } }""").json()

        assertFalse(result.getValue("passed").jsonPrimitive.boolean)
        assertEquals(
            "checkout › pay\n  ✕ purchase · firebase\n      value · Not the expected value · expects 9.5",
            result.getValue("report").jsonPrimitive.content,
        )
    }

    @Test
    fun aStepOutOfOrderOrNoFlowAtAllIsRefused() = testApplication {
        application { webViewerModule(token = "k") }
        assertEquals(HttpStatusCode.Conflict, client.call("/api/baseline/step?name=browse").status)

        client.call("/api/baseline/flow", steps)
        val outOfOrder = client.call("/api/baseline/step?name=pay")

        assertEquals(HttpStatusCode.BadRequest, outOfOrder.status)
        assertEquals("step pay runs out of order: the baseline has browse next", outOfOrder.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, client.call("/api/baseline/flow", """{ "baseline": 1 }""").status)
    }

    @Test
    fun aRecordingComesBackWhenTheFlowFinishes() = testApplication {
        application { webViewerModule(token = "k") }
        client.call("/api/baseline/flow?record=checkout&timeout=600")
        purchase(order = "T-7")

        val result = client.call("/api/baseline/finish", """{ "variables": { "orderId": "T-7" } }""").json()

        val recorded = Json.parseToJsonElement(result.getValue("recording").jsonPrimitive.content).jsonObject
        val schema = recorded.getValue("events").jsonArray.single().jsonObject.getValue("schema").jsonObject
        assertEquals("{{orderId}}", schema.getValue("properties").jsonObject.getValue("transaction_id").jsonObject.getValue("const").jsonPrimitive.content)
    }

    @Test
    fun aBoundNumberKeepsItsType() = testApplication {
        application { webViewerModule(token = "k") }
        client.call(
            "/api/baseline/flow?timeout=100",
            """{ "flow": "f", "events": [ { "name": "purchase", "channel": "firebase",
                 "schema": { "properties": { "value": { "const": "{{total}}" } } } } ] }""",
        )
        purchase(value = 9.5)

        assertTrue(client.call("/api/baseline/finish", """{ "variables": { "total": 9.5 } }""").passed())
    }

    @Test
    fun oneEventIsCheckedOnItsOwn() = testApplication {
        application { webViewerModule(token = "k") }
        Inspector.loadTrackingPlan(
            """{ "version": "1", "events": [ { "name": "purchase", "channel": "firebase", "schema": { "required": ["value"] } } ] }""",
        )
        purchase()

        assertTrue(client.call("/api/baseline/last", """{ "name": "purchase", "channel": "firebase", "plan": true }""").passed())
        assertTrue(client.call("/api/baseline/last", """{ "name": "purchase", "channel": "firebase", "property": "value", "value": 9.5 }""").passed())
        val wrong = client.call("/api/baseline/last", """{ "name": "purchase", "channel": "firebase", "property": "value", "value": 3 }""").json()
        assertEquals("purchase · firebase: value · Not the expected value · expects 3", wrong.getValue("report").jsonPrimitive.content)
    }

    @Test
    fun everyBaselineRouteIsBehindTheGate() = testApplication {
        application { webViewerModule(token = "k") }
        listOf("/api/baseline/flow", "/api/baseline/step", "/api/baseline/step/end", "/api/baseline/finish", "/api/baseline/last").forEach { path ->
            assertEquals(HttpStatusCode.Unauthorized, client.post(path) { header("X-Verivent", "1") }.status, path)
            assertEquals(HttpStatusCode.Forbidden, client.post("$path?t=k").status, path)
        }
    }
}
