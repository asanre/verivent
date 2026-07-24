package com.snoop.core

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.BodyPreview
import com.snoop.core.model.NetworkState
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.redaction.RedactionEngine
import com.snoop.core.store.SnoopStore
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

class SnoopStoreTest {

    @Test
    fun ringBufferDropsOldestBeyondCapacity() {
        val store = SnoopStore(capacity = 3)
        repeat(5) { store.record(txn(it.toString())) }

        val ids = store.entries.value.map { it.id }
        assertEquals(listOf("2", "3", "4"), ids)
    }

    @Test
    fun updateReplacesById() {
        val store = SnoopStore()
        store.record(txn("a"))
        store.update("a") { (it as NetworkTransaction).copy(status = 200) }

        val entry = store.entries.value.single() as NetworkTransaction
        assertEquals(200, entry.status)
        assertEquals(NetworkState.Complete, entry.state)
    }

    @Test
    fun clearEmptiesStore() {
        val store = SnoopStore()
        store.record(txn("a"))
        store.clear()
        assertTrue(store.entries.value.isEmpty())
    }

    @Test
    fun reconfigureKeepsTheSameStateFlow() {
        val store = SnoopStore()
        val flow = store.entries
        store.record(txn("a"))
        store.reconfigure(capacity = 2)
        store.record(txn("b"))
        assertSame(flow, store.entries)
        assertEquals(listOf("b"), flow.value.map { it.id })
    }

    @Test
    fun reconfigureWithoutClearTrimsToNewCapacity() {
        val store = SnoopStore()
        repeat(5) { store.record(txn(it.toString())) }
        store.reconfigure(capacity = 2, clear = false)
        assertEquals(listOf("3", "4"), store.entries.value.map { it.id })
    }

    @Test
    fun zeroOrNegativeCapacityThrows() {
        assertFailsWith<IllegalArgumentException> { SnoopStore(capacity = 0) }
        assertFailsWith<IllegalArgumentException> { SnoopStore().reconfigure(capacity = -1) }
    }

    @Test
    fun byteBudgetEvictsOldestOnRecord() {
        val store = SnoopStore(capacity = 100, maxTotalBytes = 2_000)
        repeat(10) { store.record(bigTxn(it.toString(), bodyChars = 700)) }
        val ids = store.entries.value.map { it.id }
        assertTrue(ids.size < 10, "expected byte-budget eviction, kept $ids")
        assertEquals("9", ids.last())
        assertEquals((10 - ids.size until 10).map { it.toString() }, ids)
    }

    @Test
    fun newestSurvivesEvenWhenItAloneExceedsTheBudget() {
        val store = SnoopStore(capacity = 100, maxTotalBytes = 1_000)
        store.record(txn("a"))
        store.record(bigTxn("b", bodyChars = 5_000))
        assertEquals(listOf("b"), store.entries.value.map { it.id })
    }

    @Test
    fun byteBudgetEvictsOldestOnGrowingUpdate() {
        val store = SnoopStore(capacity = 100, maxTotalBytes = 2_000)
        store.record(txn("a"))
        store.record(txn("b"))
        store.update("b") {
            (it as NetworkTransaction).copy(responseBody = BodyPreview("x".repeat(5_000), 5_000, false))
        }
        // The grown entry blows the budget; the oldest is evicted, the newest survives.
        assertEquals(listOf("b"), store.entries.value.map { it.id })
    }
}

class SnoopCoreConfigureTest {

    @BeforeTest
    fun setup() = SnoopCore.reset()

    @AfterTest
    fun tearDown() {
        SnoopCore.store.reconfigure(SnoopStore.DEFAULT_CAPACITY, SnoopStore.DEFAULT_MAX_TOTAL_BYTES)
        SnoopCore.reset()
    }

    @Test
    fun configureKeepsStoreAndFlowIdentityAndStillEmits() {
        val store = SnoopCore.store
        val flow = store.entries
        SnoopCore.configure(capacity = 10)
        SnoopCore.recordNetwork(txn("a"))
        assertSame(store, SnoopCore.store)
        assertSame(flow, SnoopCore.store.entries)
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
    fun fragmentIsPreserved() {
        assertEquals(
            "https://api.test/x?token=██#section",
            RedactionEngine().redactUrl("https://api.test/x?token=abc#section"),
        )
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

class SnoopCoreTest {

    @BeforeTest
    fun setup() = SnoopCore.reset()

    @AfterTest
    fun tearDown() = SnoopCore.reset()

    @Test
    fun disabledCoreDropsEntries() {
        SnoopCore.enabled = false
        SnoopCore.recordAnalytics(event("e1", SnoopCore.now()))
        assertTrue(SnoopCore.entriesSnapshot.isEmpty())
    }

    @Test
    fun idsAreUnique() {
        val ids = List(1000) { SnoopCore.nextId() }
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
