package com.snoop.core

import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.NetworkState
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.redaction.RedactionEngine
import com.snoop.core.store.SnoopStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
}

class SnoopCoreCorrelationTest {

    @BeforeTest
    fun setup() = SnoopCore.reset()

    @AfterTest
    fun tearDown() = SnoopCore.reset()

    @Test
    fun recentAnalyticsEventsAreLinkedToCall() {
        val now = SnoopCore.now()
        SnoopCore.recordAnalytics(event("e1", now))
        SnoopCore.recordNetwork(
            NetworkTransaction(
                id = "call1",
                timestamp = now,
                method = "POST",
                url = "https://c/collect",
                host = "c",
                path = "/collect",
                status = 204,
            ),
        )

        SnoopCore.correlateRecentAnalytics("call1")

        val event = SnoopCore.entriesSnapshot.filterIsInstance<AnalyticsEntry>().single()
        val call = SnoopCore.entriesSnapshot.filterIsInstance<NetworkTransaction>().single()
        assertEquals("call1", event.correlatedCallId)
        assertEquals(listOf("e1"), call.correlatedEventIds)
    }

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

private fun event(id: String, at: Instant) = AnalyticsEntry(
    id = id,
    timestamp = at,
    name = "purchase",
    channel = "firebase",
)
