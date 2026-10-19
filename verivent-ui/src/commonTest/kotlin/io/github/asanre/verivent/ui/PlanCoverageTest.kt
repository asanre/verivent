package io.github.asanre.verivent.ui

import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.PlanEvent
import io.github.asanre.verivent.core.model.PlanIssue
import io.github.asanre.verivent.core.model.PlanSummary
import io.github.asanre.verivent.ui.internal.EventCoverage
import io.github.asanre.verivent.ui.internal.planCoverage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PlanCoverageTest {

    private val search = PlanEvent("search", "firebase")
    private val addToCart = PlanEvent("add_to_cart", "server-side")
    private val checkout = PlanEvent("begin_checkout", "server-side")
    private val plan = PlanSummary("2.0.0", listOf(search, addToCart, checkout))

    private fun capture(event: PlanEvent, planVersion: String? = "2.0.0", broken: Boolean = false) = AnalyticsEntry(
        "id", Instant.fromEpochMilliseconds(0), event.name, event.channel,
        planVersion = planVersion,
        issues = if (broken) listOf(PlanIssue("q", "Required, but not sent")) else emptyList(),
    )

    @Test
    fun breaksComeFirstThenMatchesThenEventsNotSeen() {
        val coverage = planCoverage(
            listOf(capture(search), capture(addToCart), capture(addToCart, broken = true), capture(search)),
            plan,
        )
        assertEquals(
            listOf(EventCoverage(addToCart, 2, 1), EventCoverage(search, 2, 0), EventCoverage(checkout, 0, 0)),
            coverage,
        )
    }

    @Test
    fun capturesCheckedByAnotherVersionDoNotCount() {
        val coverage = planCoverage(listOf(capture(search, planVersion = "1.0.0", broken = true)), plan)
        assertEquals(0, coverage.single { it.event == search }.captured)
    }
}
