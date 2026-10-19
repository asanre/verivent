package io.github.asanre.verivent.ktor

import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.NetworkState
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeStringUtf8
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VeriventPluginTest {

    @BeforeTest fun setup() = Inspector.reset()

    @AfterTest fun tearDown() = Inspector.reset()

    private fun client(config: VeriventConfig.() -> Unit = {}) = HttpClient(MockEngine) {
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
        install(Verivent, config)
    }

    private fun transactions() = Inspector.entriesSnapshot.filterIsInstance<NetworkTransaction>()

    @Test
    fun capturesSuccessfulCall() = runTest {
        val response = client().get("https://api.test/users?page=1")
        // App still reads the body even though Verivent observed it.
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
    fun redactsSecretsInFormBodies() = runTest {
        lateinit var wireBody: String
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    wireBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    respond(
                        "access_token=abc&scope=basic",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString()),
                    )
                }
            }
            install(Verivent)
        }
        client.post("https://api.test/oauth/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("password=hunter2&user=a")
        }
        val txn = transactions().single()
        assertEquals("password=██&user=a", txn.requestBody?.text)
        assertEquals("password=hunter2&user=a", wireBody)
        assertEquals("access_token=██&scope=basic", txn.responseBody?.text)
    }

    @Test
    fun aJsonBodyThatLooksLikeAFormIsLeftAlone() = runTest {
        client().post("https://api.test/orders") {
            contentType(ContentType.Application.Json)
            setBody("password=hunter2")
        }
        assertEquals("password=hunter2", transactions().single().requestBody?.text)
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
            install(Verivent)
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

        Inspector.reset()
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
            install(Verivent)
        }
        // execute {} skips Ktor's SaveBody; Verivent must not consume the host's only channel.
        val received = client.prepareGet("https://api.test/stream").execute { it.bodyAsText() }
        assertEquals("chunk-1chunk-2", received)
        assertEquals("(streaming body — not captured)", transactions().single().responseBody?.text)
    }

    @Test
    fun responseIsDecodedWithTheDeclaredCharset() = runTest {
        // 0xE9 is 'é' in ISO-8859-1 and an invalid byte in UTF-8.
        val client = charsetClient("text/plain; charset=ISO-8859-1", byteArrayOf(0xE9.toByte()))
        client.get("https://api.test/latin").bodyAsText()
        assertEquals("é", transactions().single().responseBody?.text)
    }

    @Test
    fun anUnsupportedCharsetFallsBackInsteadOfLosingTheBody() = runTest {
        val client = charsetClient("text/plain; charset=x-made-up", "hello".encodeToByteArray())
        client.get("https://api.test/unknown").bodyAsText()
        assertEquals("hello", transactions().single().responseBody?.text)
    }

    @Test
    fun aBodyItsCharsetCannotDecodeIsStillCaptured() = runTest {
        // 0xFF is invalid in US-ASCII: the JVM decoder substitutes, Darwin's throws and falls back.
        val client = charsetClient("text/plain; charset=us-ascii", byteArrayOf(0x68, 0x69, 0xFF.toByte()))
        client.get("https://api.test/ascii")
        val text = assertNotNull(transactions().single().responseBody?.text)
        assertTrue(text.startsWith("hi"))
    }

    private fun charsetClient(contentType: String, body: ByteArray) = HttpClient(MockEngine) {
        engine {
            addHandler {
                respond(
                    content = ByteReadChannel(body),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, contentType),
                )
            }
        }
        install(Verivent)
    }

    @Test
    fun binaryResponseShowsPlaceholder() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond("PNG-BYTES", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/octet-stream"))
                }
            }
            install(Verivent)
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

    @Test
    fun transportErrorMessageIsRedacted() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                // Ktor's own timeout message has this shape: the failing URL, query string and all.
                addHandler { throw RuntimeException("Request timeout [url=https://api.test/u?token=hunter2]") }
            }
            install(Verivent)
        }
        assertFailsWith<RuntimeException> { client.get("https://api.test/u?token=hunter2") }

        val error = assertNotNull(transactions().single().error)
        assertTrue("hunter2" !in error, "unredacted secret in captured error: $error")
        assertTrue("token=██" in error, "expected the redaction placeholder, got: $error")
    }

    @Test
    fun aTimeoutWhileReadingTheResponseIsRecordedNotLogged() = runTest {
        val timeout = HttpRequestTimeoutException("https://api.test/slow?token=hunter2", 10_000)
        // The call timed out after its headers arrived: every read of its body fails, Verivent's and the host's.
        val deadCall = createClientPlugin("DeadCall") {
            client.responsePipeline.intercept(HttpResponsePipeline.Receive) { throw timeout }
        }
        val client = HttpClient(MockEngine) {
            engine { addHandler { respond("late", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/plain")) } }
            install(deadCall)
            install(Verivent)
        }
        assertFailsWith<HttpRequestTimeoutException> {
            client.get("https://api.test/slow?token=hunter2").bodyAsText()
        }

        // The host's own call reports the timeout; the timeline shows it too instead of a pending call.
        val txn = transactions().single()
        assertEquals(NetworkState.Failed, txn.state)
        val error = assertNotNull(txn.error)
        assertTrue("hunter2" !in error, "unredacted secret in captured error: $error")
        assertTrue("token=██" in error, "expected the redaction placeholder, got: $error")
    }

    @Test
    fun headerPredicateMatchesRegardlessOfCase() = runTest {
        // HTTP/2 lowercases header names, so a rule written in either spelling has to match.
        client { sanitizeHeader { it == "x-api-key" } }
            .get("https://api.test/") { header("X-Api-Key", "key-123") }
        assertTrue(transactions().single().requestHeaders.single { it.name == "X-Api-Key" }.redacted)
    }

    @Test
    fun truncationNeverSplitsAMultibyteCharacter() = runTest {
        // "é" is two bytes, so a 5-byte limit lands mid-character.
        client { maxContentLength = 5 }.post("https://api.test/orders") { setBody("ééé") }

        val body = assertNotNull(transactions().single().requestBody)
        assertTrue(body.truncated)
        assertEquals("éé", body.text)
        assertTrue('�' !in body.text, "truncation split a UTF-8 sequence: ${body.text}")
        assertEquals(6, body.byteCount)
    }

    @Test
    fun disabledCoreSkipsResponseCaptureEntirely() = runTest {
        val client = client()
        client.get("https://api.test/users")
        Inspector.enabled = false
        try {
            assertEquals("response-body", client.get("https://api.test/users").bodyAsText())
        } finally {
            Inspector.enabled = true
        }
        // The disabled call is neither recorded nor patched onto the earlier one.
        assertEquals(1, transactions().size)
        assertEquals(200, transactions().single().status)
    }

    @Test
    fun eachRetryAttemptIsRecordedWithItsOwnOutcome() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    attempts++
                    if (attempts == 1) {
                        respond("nope", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond("ok", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/plain"))
                    }
                }
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 1)
                constantDelay(millis = 1, randomizationMs = 0)
            }
            install(Verivent)
        }
        assertEquals("ok", client.get("https://api.test/flaky").bodyAsText())

        // Ktor gives every attempt a fresh attribute set, so attempts can't be folded into one
        // entry. Both are recorded and — for a status-code retry — both carry their real outcome.
        assertEquals(2, attempts)
        assertEquals(listOf(503, 200), transactions().map { it.status })
        assertTrue(transactions().none { it.state == NetworkState.Pending })
    }
}
