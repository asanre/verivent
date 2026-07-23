package com.snoop.ktor

import com.snoop.core.SnoopCore
import com.snoop.core.model.NetworkState
import com.snoop.core.model.NetworkTransaction
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SnoopPluginTest {

    @BeforeTest fun setup() = SnoopCore.reset()

    @AfterTest fun tearDown() = SnoopCore.reset()

    private fun client(config: SnoopConfig.() -> Unit = {}) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                when (request.url.encodedPath) {
                    "/boom" -> throw RuntimeException("network down")
                    else -> respond(
                        content = "response-body",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
        install(Snoop, config)
    }

    private fun transactions() = SnoopCore.entriesSnapshot.filterIsInstance<NetworkTransaction>()

    @Test
    fun capturesSuccessfulCall() = runTest {
        val response = client().get("https://api.test/users?page=1")
        // App still reads the body even though Snoop observed it.
        assertEquals("response-body", response.bodyAsText())

        val txn = transactions().single()
        assertEquals("GET", txn.method)
        assertEquals("api.test", txn.host)
        assertEquals("/users", txn.path)
        assertEquals(200, txn.status)
        assertEquals(NetworkState.Complete, txn.state)
        assertEquals("response-body", txn.responseBody?.text)
        assertNotNull(txn.durationMillis)
    }

    @Test
    fun redactsSensitiveHeaders() = runTest {
        client().get("https://api.test/") {
            header(HttpHeaders.Authorization, "Bearer super-secret")
            header("X-Api-Key", "key-123")
        }
        val txn = transactions().single()
        val auth = txn.requestHeaders.single { it.name == HttpHeaders.Authorization }
        assertTrue(auth.redacted)
        assertEquals("██", auth.value)
        // Non-default header stays visible unless a predicate matches it.
        assertEquals("key-123", txn.requestHeaders.single { it.name == "X-Api-Key" }.value)
    }

    @Test
    fun customHeaderPredicateRedacts() = runTest {
        client { sanitizeHeader { it == "X-Api-Key" } }
            .get("https://api.test/") { header("X-Api-Key", "key-123") }
        val txn = transactions().single()
        assertTrue(txn.requestHeaders.single { it.name == "X-Api-Key" }.redacted)
    }

    @Test
    fun filterSkipsMatchingRequests() = runTest {
        client { filter { it.url.host != "metrics.test" } }
            .get("https://metrics.test/collect")
        assertTrue(transactions().isEmpty())
    }

    @Test
    fun capturesRequestBody() = runTest {
        client().post("https://api.test/orders") {
            setBody("{\"id\":42}")
        }
        val txn = transactions().single()
        assertEquals("{\"id\":42}", txn.requestBody?.text)
    }

    @Test
    fun truncatesLargeBodies() = runTest {
        client { maxContentLength = 5 }.post("https://api.test/orders") {
            setBody("0123456789")
        }
        val body = transactions().single().requestBody
        assertNotNull(body)
        assertTrue(body.truncated)
        assertEquals(5, body.text.length)
        assertEquals(10, body.byteCount)
    }

    @Test
    fun redactsQueryParametersInCapturedUrlOnly() = runTest {
        lateinit var wireUrl: String
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    wireUrl = request.url.toString()
                    respond("ok", HttpStatusCode.OK)
                }
            }
            install(Snoop)
        }
        client.get("https://api.test/users?token=secret&page=1")

        // The wire saw the real URL; only the captured copy is redacted.
        assertEquals("https://api.test/users?token=secret&page=1", wireUrl)
        assertEquals("https://api.test/users?token=██&page=1", transactions().single().url)
    }

    @Test
    fun sanitizeBodyAppliesToBothPreviews() = runTest {
        client { sanitizeBody { it.replace("secret", "***") } }
            .post("https://api.test/orders") { setBody("request-secret") }
        val txn = transactions().single()
        assertEquals("request-***", txn.requestBody?.text)
        assertEquals("response-body", txn.responseBody?.text)

        SnoopCore.reset()
        client { sanitizeBody { it.replace("body", "***") } }.get("https://api.test/users")
        assertEquals("response-***", transactions().single().responseBody?.text)
    }

    @Test
    fun streamingResponseReachesTheHostIntact() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    val channel = ByteChannel(autoFlush = true)
                    channel.writeStringUtf8("chunk-1")
                    channel.writeStringUtf8("chunk-2")
                    channel.flushAndClose()
                    respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            install(Snoop)
        }
        // execute {} skips Ktor's SaveBody; Snoop must not consume the host's only channel.
        val received = client.prepareGet("https://api.test/stream").execute { it.bodyAsText() }
        assertEquals("chunk-1chunk-2", received)
        assertEquals("(streaming body — not captured)", transactions().single().responseBody?.text)
    }

    @Test
    fun binaryResponseShowsPlaceholder() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond("PNG-BYTES", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/octet-stream"))
                }
            }
            install(Snoop)
        }
        client.get("https://api.test/image")
        assertEquals(
            "(binary body: application/octet-stream — not captured)",
            transactions().single().responseBody?.text,
        )
    }

    @Test
    fun oversizeResponseIsTruncatedWithFullByteCount() = runTest {
        val response = client { maxContentLength = 5 }.get("https://api.test/users")
        assertEquals("response-body", response.bodyAsText())

        val body = transactions().single().responseBody
        assertNotNull(body)
        assertTrue(body.truncated)
        assertEquals(5, body.text.length)
        assertEquals(13, body.byteCount)
    }

    @Test
    fun capturesTransportError() = runTest {
        assertFailsWith<RuntimeException> {
            client().get("https://api.test/boom")
        }
        val txn = transactions().single()
        assertEquals(NetworkState.Failed, txn.state)
        assertNotNull(txn.error)
    }
}
