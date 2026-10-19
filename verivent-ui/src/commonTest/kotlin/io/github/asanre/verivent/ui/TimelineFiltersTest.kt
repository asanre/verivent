package io.github.asanre.verivent.ui

import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.core.model.PlanIssue
import io.github.asanre.verivent.core.model.PlanSummary
import io.github.asanre.verivent.ui.internal.TimelineFilters
import io.github.asanre.verivent.ui.internal.TypeFilter
import io.github.asanre.verivent.ui.internal.matchesFilters
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

    @Test
    fun breaksPlanForcesAnalyticsTypeAndNetworkClearsIt() {
        val filters = TimelineFilters(TypeFilter.Network).toggleBreaksPlan()
        assertEquals(TypeFilter.Analytics, filters.type)
        assertTrue(filters.breaksPlan)
        assertFalse(filters.selectType(TypeFilter.Network).breaksPlan)
    }

    @Test
    fun breaksPlanKeepsOnlyEventsTheLoadedPlanFoundBroken() {
        fun event(issues: List<PlanIssue>) =
            AnalyticsEntry("1", Instant.fromEpochMilliseconds(0), "search", "firebase", planVersion = "1", issues = issues)
        val breaking = event(listOf(PlanIssue("q", "Required, but not sent")))
        val onlyBreaking = TimelineFilters().toggleBreaksPlan()
        val loaded = PlanSummary("1", emptyList())
        assertTrue(matchesFilters(breaking, onlyBreaking, "", loaded))
        assertFalse(matchesFilters(event(emptyList()), onlyBreaking, "", loaded))
        assertFalse(matchesFilters(breaking, onlyBreaking, "", PlanSummary("2", emptyList())))
        assertFalse(matchesFilters(breaking, onlyBreaking, "", plan = null))
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

    private fun matches(query: String) = matchesFilters(call, TimelineFilters(), query, plan = null)

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
