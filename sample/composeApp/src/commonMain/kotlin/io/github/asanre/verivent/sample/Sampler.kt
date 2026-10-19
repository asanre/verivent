package io.github.asanre.verivent.sample

import io.github.asanre.verivent.analytics.AnalyticsSink
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.Annotation
import io.github.asanre.verivent.ktor.Verivent
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/** Wires a real Ktor client (with Verivent installed) and a Verivent analytics logger for the demo. */
class Sampler {

    private val client = HttpClient(sampleEngine()) {
        install(Verivent) {
            maxContentLength = 200_000
            sanitizeHeader { it == HttpHeaders.Authorization }
        }
    }

    private val ecommerce = setOf("purchase", "add_to_cart")
    private val analytics = AnalyticsSink {
        filter { !it.name.startsWith("debug_") }
        sanitizeProperty { it == "user_email" }
        annotate { if (it.name in ecommerce) listOf(Annotation.Ecommerce) else emptyList() }
        logTo()
    }

    suspend fun getSuccess() {
        client.get("https://jsonplaceholder.typicode.com/todos/1").bodyAsText()
    }

    suspend fun getNotFound() {
        client.get("https://jsonplaceholder.typicode.com/todos/does-not-exist").bodyAsText()
    }

    suspend fun postOrder() {
        client.post("https://jsonplaceholder.typicode.com/posts") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"verivent","body":"demo","userId":1}""")
        }.bodyAsText()
    }

    suspend fun triggerError() {
        client.get("https://verivent.invalid.host.example/timeout").bodyAsText()
    }

    fun logScreenView(screen: String) {
        analytics.log("screen_view", channel = "firebase", properties = mapOf("screen_name" to screen))
    }

    fun logPurchase() {
        analytics.log(
            name = "purchase",
            channel = "firebase",
            properties = mapOf("value" to 42.5, "currency" to "EUR", "user_email" to "demo@verivent.dev"),
            sections = mapOf(
                "Default parameters" to mapOf("environment" to "demo", "language" to "en"),
                "User properties" to mapOf("user_logged" to true, "newsletter_subscriber" to "no"),
            ),
        )
    }

    fun logServerSide() {
        analytics.log("checkout_started", channel = "gtm", properties = mapOf("cart_size" to 3))
    }

    /**
     * A plan these demo events partly break: screen_view sends a screen it doesn't list, purchase
     * misses transaction_id and sends user_email, the gtm events match, and the rest aren't logged.
     */
    fun loadTrackingPlan() = Inspector.loadTrackingPlan(SAMPLE_PLAN)

    /**
     * Log a batch of events and immediately flush them to a collector endpoint — the shape a
     * client-side GTM/GA4 setup produces. The demo endpoint answers 404; the timeline shows the
     * two events followed by the flush call.
     */
    suspend fun flushToCollector() {
        analytics.log("add_to_cart", channel = "gtm", properties = mapOf("item" to "SN-42"))
        analytics.log("checkout_started", channel = "gtm", properties = mapOf("cart_size" to 3))
        client.post("https://jsonplaceholder.typicode.com/collect") {
            contentType(ContentType.Application.Json)
            setBody("""{"events":["add_to_cart","checkout_started"]}""")
        }.bodyAsText()
    }
}

private val SAMPLE_PLAN = """
{ "version": "0.1.0", "events": [
  { "name": "screen_view", "channel": "firebase",
    "schema": { "required": ["screen_name"],
                "properties": { "screen_name": { "type": "string", "enum": ["search", "catalog", "pdp"] } } } },
  { "name": "purchase", "channel": "firebase",
    "schema": { "required": ["value", "currency", "transaction_id"],
                "properties": { "value": { "type": "number" }, "currency": { "type": "string", "pattern": "^[A-Z]{3}$" } },
                "additionalProperties": false } },
  { "name": "add_to_cart", "channel": "gtm",
    "schema": { "required": ["item"], "properties": { "item": { "type": "string", "pattern": "^SN-[0-9]+$" } } } },
  { "name": "checkout_started", "channel": "gtm",
    "schema": { "required": ["cart_size"], "properties": { "cart_size": { "type": "integer" } } } },
  { "name": "view_item_list", "channel": "firebase", "schema": {} },
  { "name": "view_item", "channel": "firebase", "schema": {} },
  { "name": "begin_checkout", "channel": "server-side", "schema": {} },
  { "name": "add_payment_info", "channel": "server-side", "schema": {} },
  { "name": "refund", "channel": "firebase", "schema": {} }
] }
"""
