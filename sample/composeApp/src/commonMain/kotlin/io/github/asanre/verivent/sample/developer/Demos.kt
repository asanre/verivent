package io.github.asanre.verivent.sample.developer

import io.github.asanre.verivent.analytics.AnalyticsSink
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** What the Developer screen fires on purpose: events that break the plan and calls that show capture. */
class Demos(private val analytics: AnalyticsSink, private val client: HttpClient) {

    /** A purchase without its transaction id, and with a quantity sent as text. */
    fun brokenPurchase() = analytics.log(
        "purchase",
        "firebase",
        mapOf("currency" to "EUR", "value" to 4.5, "items" to listOf(mapOf("item_id" to "SN-7", "quantity" to "1"))),
    )

    /** Long enough for `logTo` to split it on every platform; two of its 60 items break the plan. */
    fun longItemList() = analytics.log(
        "view_item_list",
        "firebase",
        mapOf(
            "item_list_name" to "catalog",
            "items" to (1..60).map { index ->
                mapOf(
                    "item_id" to if (index % 30 == 0) "sn-$index" else "SN-$index",
                    "item_name" to "Item $index",
                    "price" to 0.99,
                    "index" to index,
                )
            },
        ),
    )

    /** Dropped by the sample's filter: it never reaches the timeline. */
    fun debugPing() = analytics.log("debug_ping", "internal")

    suspend fun call(call: DemoCall) {
        when (call) {
            DemoCall.Get -> client.get("$HOST/todos/1")
            DemoCall.NotFound -> client.get("$HOST/todos/does-not-exist")
            DemoCall.PostJson -> client.post("$HOST/posts") {
                contentType(ContentType.Application.Json)
                setBody("""{"title":"verivent","body":"demo","userId":1}""")
            }
            DemoCall.PostForm -> client.post("$HOST/posts") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("user=ana&password=hunter2")
            }
            DemoCall.Secrets -> client.get("$HOST/todos/1?api_key=supersecret") {
                header("Authorization", "Bearer nope")
                header("X-Api-Key", "also-secret")
            }
            DemoCall.ResetToken -> client.get("$HOST/reset/9f8e7d6c")
            DemoCall.Unreachable -> client.get("https://verivent.invalid.host.example/timeout")
        }.bodyAsText()
    }
}

/** The Developer screen's network buttons, the same on both samples. */
enum class DemoCall(val title: String) {
    Get("GET a todo"),
    NotFound("GET something missing"),
    PostJson("POST a JSON body"),
    PostForm("POST a form with a password"),
    Secrets("GET with secrets in header and query"),
    ResetToken("GET a path with a reset token"),
    Unreachable("GET an unreachable host"),
}

private const val HOST = "https://jsonplaceholder.typicode.com"
