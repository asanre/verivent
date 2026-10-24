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

    private fun capture(event: PlanEvent, planVersion: String? = "2.0.0", broken: Boolean = false, variant: String? = null) = AnalyticsEntry(
        "id", Instant.fromEpochMilliseconds(0), event.name, event.channel,
        planVersion = planVersion,
        issues = if (broken) listOf(PlanIssue("q", "Required, but not sent")) else emptyList(),
        variant = variant,
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

    @Test
    fun anEventWithVariantsCountsEachVariantOnItsOwn() {
        val tap = PlanEvent("ui_interaction", "firebase")
        val plan = PlanSummary("2.0.0", listOf(tap, search), variants = mapOf(tap to listOf("home_banner", "share_playlist", "like_track")))

        val coverage = planCoverage(
            listOf(
                capture(tap, variant = "share_playlist"),
                capture(tap, variant = "share_playlist", broken = true),
                capture(tap, variant = "home_banner"),
                capture(tap, broken = true),
            ),
            plan,
        )

        assertEquals(
            listOf(
                EventCoverage(tap, 2, 1, label = "ui_interaction › share_playlist"),
                EventCoverage(tap, 1, 1, label = "ui_interaction › no planned variant"),
                EventCoverage(tap, 1, 0, label = "ui_interaction › home_banner"),
                EventCoverage(tap, 0, 0, label = "ui_interaction › like_track"),
                EventCoverage(search, 0, 0),
            ),
            coverage,
        )
    }

    @Test
    fun noPlannedVariantOnlyShowsWhenSomethingWasSentAsNone() {
        val tap = PlanEvent("ui_interaction", "firebase")
        val plan = PlanSummary("2.0.0", listOf(tap), variants = mapOf(tap to listOf("home_banner")))
        assertEquals(listOf("ui_interaction › home_banner"), planCoverage(emptyList(), plan).map { it.label })
    }
}
