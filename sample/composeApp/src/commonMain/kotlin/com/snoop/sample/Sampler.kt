package com.snoop.sample

import com.snoop.analytics.SnoopAnalytics
import com.snoop.core.model.Annotation
import com.snoop.ktor.Snoop
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/** Wires a real Ktor client (with Snoop installed) and a Snoop analytics logger for the demo. */
class Sampler {

    private val client = HttpClient(sampleEngine()) {
        install(Snoop) {
            maxContentLength = 200_000
            sanitizeHeader { it == HttpHeaders.Authorization }
        }
    }

    private val ecommerce = setOf("purchase", "add_to_cart")
    private val analytics = SnoopAnalytics {
        filter { !it.name.startsWith("debug_") }
        sanitizeProperty { it == "user_email" }
        annotate { if (it.name in ecommerce) listOf(Annotation.Ecommerce) else emptyList() }
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
            setBody("""{"title":"snoop","body":"demo","userId":1}""")
        }.bodyAsText()
    }

    suspend fun triggerError() {
        client.get("https://snoop.invalid.host.example/timeout").bodyAsText()
    }

    fun logScreenView(screen: String) {
        analytics.log("screen_view", channel = "firebase", properties = mapOf("screen" to screen))
    }

    fun logPurchase() {
        analytics.log(
            name = "purchase",
            channel = "firebase",
            properties = mapOf("value" to 42.5, "currency" to "EUR", "user_email" to "demo@snoop.dev"),
        )
    }

    fun logServerSide() {
        analytics.log("checkout_started", channel = "gtm", properties = mapOf("cart_size" to 3))
    }

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
