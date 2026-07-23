package com.snoop.ui

import com.snoop.ui.internal.TimelineFilters
import com.snoop.ui.internal.TypeFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
