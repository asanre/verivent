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
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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
    fun capturesTransportError() = runTest {
        assertFailsWith<RuntimeException> {
            client().get("https://api.test/boom")
        }
        val txn = transactions().single()
        assertEquals(NetworkState.Failed, txn.state)
        assertNotNull(txn.error)
    }
}
