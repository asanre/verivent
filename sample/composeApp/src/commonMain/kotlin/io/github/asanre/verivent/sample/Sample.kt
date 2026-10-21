package io.github.asanre.verivent.sample

import io.github.asanre.verivent.analytics.AnalyticsSink
import io.github.asanre.verivent.core.model.Annotation
import io.github.asanre.verivent.ktor.Verivent
import io.github.asanre.verivent.sample.shop.ShopAnalytics
import io.github.asanre.verivent.sample.shop.Cart
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodedPath

/** The sample's one analytics sink and one HTTP client, wired to Verivent as an app would. */
object Sample {

    private val ecommerce = setOf("view_item", "add_to_cart", "begin_checkout", "purchase")

    val analytics = AnalyticsSink {
        filter { !it.name.startsWith("debug_") }
        sanitizeProperty { it == "user_email" }
        annotate { if (it.name in ecommerce) listOf(Annotation.Ecommerce) else emptyList() }
        groupBy("screen_view", labelProperty = "screen_name")
        logTo()
    }

    val shop = ShopAnalytics(analytics)

    val client = HttpClient(sampleEngine()) {
        install(Verivent) {
            sanitizeHeader { it.equals("X-Api-Key", ignoreCase = true) }
            sanitizePath { it.replace(Regex("^/reset/[^/]+"), "/reset/██") }
            filter { it.url.encodedPath != "/health" }
        }
    }

    /** The order the shop sends when it's paid, to a demo endpoint: network next to the purchase event. */
    suspend fun placeOrder(cart: Cart, orderId: String) {
        client.post("https://jsonplaceholder.typicode.com/posts") {
            contentType(ContentType.Application.Json)
            setBody("""{"order":"$orderId","items":${cart.size}}""")
        }
    }
}
