package com.snoop.ui

import com.snoop.core.model.NetworkTransaction
import com.snoop.ui.internal.TimelineFilters
import com.snoop.ui.internal.TypeFilter
import com.snoop.ui.internal.matchesFilters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TimelineFiltersTest {

    @Test
    fun selectingChannelForcesAnalyticsType() {
        val filters = TimelineFilters(TypeFilter.Network).selectChannel("firebase")
        assertEquals(TypeFilter.Analytics, filters.type)
        assertEquals("firebase", filters.channel)
    }

    @Test
    fun selectingNetworkClearsChannel() {
        val filters = TimelineFilters(TypeFilter.Analytics, "firebase").selectType(TypeFilter.Network)
        assertEquals(TypeFilter.Network, filters.type)
        assertNull(filters.channel)
    }

    @Test
    fun selectingAllKeepsChannel() {
        val filters = TimelineFilters(TypeFilter.Analytics, "firebase").selectType(TypeFilter.All)
        assertEquals(TypeFilter.All, filters.type)
        assertEquals("firebase", filters.channel)
    }

    @Test
    fun reselectingChannelTogglesItOff() {
        val filters = TimelineFilters(TypeFilter.Analytics, "firebase").selectChannel("firebase")
        assertEquals(TypeFilter.Analytics, filters.type)
        assertNull(filters.channel)
    }

    @Test
    fun switchingChannelReplacesSelection() {
        val filters = TimelineFilters(TypeFilter.Analytics, "firebase").selectChannel("amplitude")
        assertEquals("amplitude", filters.channel)
    }
}

class SearchMatchingTest {

    private val call = NetworkTransaction(
        id = "1",
        timestamp = Instant.fromEpochMilliseconds(0),
        method = "GET",
        url = "https://api.test/users?campaign=summer",
        host = "api.test",
        path = "/users",
    )

    private fun matches(query: String) = matchesFilters(call, TypeFilter.All, null, query)

    @Test
    fun searchReachesTheQueryString() {
        assertTrue(matches("campaign"))
        assertTrue(matches("summer"))
    }

    @Test
    fun searchStillMatchesPathHostAndMethod() {
        assertTrue(matches("users"))
        assertTrue(matches("api.test"))
        assertTrue(matches("get"))
        assertFalse(matches("nothing-here"))
    }
}
