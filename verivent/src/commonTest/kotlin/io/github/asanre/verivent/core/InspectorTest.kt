package io.github.asanre.verivent.core

import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.BodyPreview
import io.github.asanre.verivent.core.model.NetworkState
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.core.redaction.RedactionEngine
import io.github.asanre.verivent.core.store.TimelineStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class TimelineStoreTest {

    @Test
    fun ringBufferDropsOldestBeyondCapacity() {
        val store = TimelineStore(capacity = 3)
        repeat(5) { store.record(txn(it.toString())) }

        val ids = store.entries.value.map { it.id }
        assertEquals(listOf("2", "3", "4"), ids)
    }

    @Test
    fun updateReplacesById() {
        val store = TimelineStore()
        store.record(txn("a"))
        store.update("a") { (it as NetworkTransaction).copy(status = 200) }

        val entry = store.entries.value.single() as NetworkTransaction
        assertEquals(200, entry.status)
        assertEquals(NetworkState.Complete, entry.state)
    }

    @Test
    fun clearEmptiesStore() {
        val store = TimelineStore()
        store.record(txn("a"))
        store.clear()
        assertTrue(store.entries.value.isEmpty())
    }

    @Test
    fun reconfigureKeepsTheSameStateFlow() {
        val store = TimelineStore()
        val flow = store.entries
        store.record(txn("a"))
        store.reconfigure(capacity = 2)
        store.record(txn("b"))
        assertSame(flow, store.entries)
        assertEquals(listOf("b"), flow.value.map { it.id })
    }

    @Test
    fun reconfigureWithoutClearTrimsToNewCapacity() {
        val store = TimelineStore()
        repeat(5) { store.record(txn(it.toString())) }
        store.reconfigure(capacity = 2, clear = false)
        assertEquals(listOf("3", "4"), store.entries.value.map { it.id })
    }

    @Test
    fun zeroOrNegativeCapacityThrows() {
        assertFailsWith<IllegalArgumentException> { TimelineStore(capacity = 0) }
        assertFailsWith<IllegalArgumentException> { TimelineStore().reconfigure(capacity = -1) }
    }

    @Test
    fun byteBudgetEvictsOldestOnRecord() {
        val store = TimelineStore(capacity = 100, maxTotalBytes = 2_000)
        repeat(10) { store.record(bigTxn(it.toString(), bodyChars = 700)) }
        val ids = store.entries.value.map { it.id }
        assertTrue(ids.size < 10, "expected byte-budget eviction, kept $ids")
        assertEquals("9", ids.last())
        assertEquals((10 - ids.size until 10).map { it.toString() }, ids)
    }

    @Test
    fun newestSurvivesEvenWhenItAloneExceedsTheBudget() {
        val store = TimelineStore(capacity = 100, maxTotalBytes = 1_000)
        store.record(txn("a"))
        store.record(bigTxn("b", bodyChars = 5_000))
        assertEquals(listOf("b"), store.entries.value.map { it.id })
    }

    @Test
    fun byteBudgetEvictsOldestOnGrowingUpdate() {
        val store = TimelineStore(capacity = 100, maxTotalBytes = 2_000)
        store.record(txn("a"))
        store.record(txn("b"))
        store.update("b") {
            (it as NetworkTransaction).copy(responseBody = BodyPreview("x".repeat(5_000), 5_000, false))
        }
        // The grown entry blows the budget; the oldest is evicted, the newest survives.
        assertEquals(listOf("b"), store.entries.value.map { it.id })
    }
}

class InspectorConfigureTest {

    @BeforeTest
    fun setup() = Inspector.reset()

    @AfterTest
    fun tearDown() {
        Inspector.store.reconfigure(TimelineStore.DEFAULT_CAPACITY, TimelineStore.DEFAULT_MAX_TOTAL_BYTES)
        Inspector.reset()
    }

    @Test
    fun titleDefaultsToVeriventAndConfigureRoundTripsIt() {
        assertEquals("Verivent", Inspector.title)

        Inspector.configure(title = "Patata Inspector")
        assertEquals("Patata Inspector", Inspector.title)

        // Touching only the limits must not silently rename the viewers back.
        Inspector.configure(capacity = 10)
        assertEquals("Patata Inspector", Inspector.title)

        Inspector.reset()
        assertEquals("Verivent", Inspector.title)
    }

    @Test
    fun configureKeepsStoreAndFlowIdentityAndStillEmits() {
        val store = Inspector.store
        val flow = store.entries
        Inspector.configure(capacity = 10)
        Inspector.recordNetwork(txn("a"))
        assertSame(store, Inspector.store)
        assertSame(flow, Inspector.store.entries)
        assertEquals(listOf("a"), flow.value.map { it.id })
    }
}

class RedactionEngineTest {

    @Test
    fun defaultHeadersAreRedactedCaseInsensitively() {
        val engine = RedactionEngine()
        val auth = engine.redactHeader("Authorization", "Bearer secret")
        assertTrue(auth.redacted)
        assertEquals("██", auth.value)
    }

    @Test
    fun customPredicateRedactsProperties() {
        val engine = RedactionEngine(propertyPredicates = listOf { it == "user_email" })
        val redacted = engine.redactProperty("user_email", "a@b.com")
        val kept = engine.redactProperty("screen", "home")
        assertTrue(redacted.redacted)
        assertFalse(kept.redacted)
        assertEquals("home", kept.value)
    }

    @Test
    fun defaultQueryParamsAreRedacted() {
        val engine = RedactionEngine()
        assertEquals(
            "https://api.test/x?api_secret=██&page=1",
            engine.redactUrl("https://api.test/x?api_secret=shh&page=1"),
        )
    }

    @Test
    fun customQueryPredicateRedacts() {
        val engine = RedactionEngine(queryParamPredicates = listOf { it == "session" })
        assertEquals(
            "https://api.test/x?session=██&page=1",
            engine.redactUrl("https://api.test/x?session=abc&page=1"),
        )
    }

    @Test
    fun urlWithoutQueryPassesThrough() {
        assertEquals("https://api.test/x", RedactionEngine().redactUrl("https://api.test/x"))
    }

    @Test
    fun fragmentParametersAreRedactedToo() {
        val engine = RedactionEngine()
        // The OAuth implicit flow hands the token back in the fragment, with no query at all.
        assertEquals(
            "https://api.test/cb#access_token=██&state=xyz",
            engine.redactUrl("https://api.test/cb#access_token=abc&state=xyz"),
        )
        assertEquals(
            "https://api.test/x?token=██#id_token=██",
            engine.redactUrl("https://api.test/x?token=abc#id_token=def"),
        )
    }

    @Test
    fun aFragmentThatIsNotParametersSurvives() {
        val engine = RedactionEngine()
        assertEquals("https://api.test/x?token=██#section", engine.redactUrl("https://api.test/x?token=abc#section"))
        assertEquals("https://api.test/x#/route", engine.redactUrl("https://api.test/x#/route"))
        // A hash-routed app keeps its own query after the route.
        assertEquals(
            "https://app.test/#/callback?access_token=██&state=xyz",
            engine.redactUrl("https://app.test/#/callback?access_token=abc&state=xyz"),
        )
        assertEquals("https://app.test/#/route?page=2", engine.redactUrl("https://app.test/#/route?page=2"))
        // Nothing to redact anywhere: the very same string comes back, not a rebuilt copy.
        val untouched = "https://api.test/x?page=1#/route"
        assertSame(untouched, engine.redactUrl(untouched))
    }

    @Test
    fun formBodiesUseTheQueryParameterRules() {
        val engine = RedactionEngine()
        assertEquals(
            "grant_type=password&username=a&password=██&client_secret=██",
            engine.redactFormBody("grant_type=password&username=a&password=hunter2&client_secret=shh"),
        )
    }

    @Test
    fun formBodyPassesThroughWhatIsNotAParameter() {
        val engine = RedactionEngine(queryParamPredicates = listOf { it == "session" })
        // A valueless pair and a plain (non-form) payload survive untouched.
        assertEquals("flag&session=██", engine.redactFormBody("flag&session=abc"))
        assertEquals("""{"a":1}""", engine.redactFormBody("""{"a":1}"""))
    }

    @Test
    fun bodyTransformsApplyInOrder() {
        val engine = RedactionEngine(bodyTransforms = listOf({ it.replace("a", "b") }, { it.replace("b", "c") }))
        assertEquals("cc", engine.redactBody("ab"))
        assertEquals("untouched", RedactionEngine().redactBody("untouched"))
    }

    @Test
    fun throwingBodyTransformFailsClosed() {
        val engine = RedactionEngine(bodyTransforms = listOf { error("host bug") })
        assertEquals("(body sanitizer failed)", engine.redactBody("card=4111111111111111"))
    }

    @Test
    fun predicatesMatchEitherCaseOfTheName() {
        // HTTP/2 lowercases header names; a rule written in either spelling has to match.
        assertTrue(RedactionEngine(headerPredicates = listOf { it == "x-api-key" }).redactHeader("X-Api-Key", "v").redacted)
        assertTrue(RedactionEngine(headerPredicates = listOf { it == "X-Api-Key" }).redactHeader("X-Api-Key", "v").redacted)
        assertFalse(RedactionEngine(headerPredicates = listOf { it == "other" }).redactHeader("X-Api-Key", "v").redacted)
    }

    @Test
    fun throwingPredicateFailsClosed() {
        val engine = RedactionEngine(queryParamPredicates = listOf { error("host bug") })
        assertEquals(
            "https://api.test/x?page=██",
            engine.redactUrl("https://api.test/x?page=1"),
        )
        assertTrue(
            RedactionEngine(headerPredicates = listOf { error("host bug") })
                .redactHeader("X-Custom", "v").redacted,
        )
    }
}

class InspectorTest {

    @BeforeTest
    fun setup() = Inspector.reset()

    @AfterTest
    fun tearDown() = Inspector.reset()

    @Test
    fun disabledCoreDropsEntries() {
        Inspector.enabled = false
        Inspector.recordAnalytics(event("e1", Inspector.now()))
        assertTrue(Inspector.entriesSnapshot.isEmpty())
    }

    @Test
    fun idsAreUnique() {
        val ids = List(1000) { Inspector.nextId() }
        assertEquals(1000, ids.toSet().size)
        assertNull(ids.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 })
    }
}

private fun txn(id: String) = NetworkTransaction(
    id = id,
    timestamp = Instant.fromEpochMilliseconds(id.hashCode().toLong()),
    method = "GET",
    url = "https://h/$id",
    host = "h",
    path = "/$id",
)

private fun bigTxn(id: String, bodyChars: Int) = txn(id).copy(
    responseBody = BodyPreview("x".repeat(bodyChars), bodyChars.toLong(), false),
)

private fun event(id: String, at: Instant) = AnalyticsEntry(
    id = id,
    timestamp = at,
    name = "purchase",
    channel = "firebase",
)
