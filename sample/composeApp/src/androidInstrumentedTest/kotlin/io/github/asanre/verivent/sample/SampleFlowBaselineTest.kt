package io.github.asanre.verivent.sample

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asanre.verivent.baseline.Baseline
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.AnalyticsEntry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The sample's analytics, sent by tapping its buttons, checked against a recorded baseline. */
class SampleFlowBaselineTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val variables = mapOf("screen" to "home", "item_id" to "SN-42")

    @Before
    fun startClean() {
        // The activity has already loaded the events a previous run left on disk.
        Inspector.reset()
    }

    /** Writes the baseline to the app's external files, to `adb pull` when the flow changes. */
    @Test
    fun recordsTheFlow() {
        runFlow()

        val baseline = Baseline.record()
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        File(target.getExternalFilesDir(null), FIXTURE).writeText(baseline)
        assertNull(Baseline.diff(baseline))
    }

    @Test
    fun flowMatchesTheBaseline() {
        runFlow()

        assertNull(Baseline.diff(fixture(), variables = variables))
    }

    @Test
    fun brokenParameterFailsWithAReadableDiff() {
        runFlow()
        // A baseline expecting a value the app no longer sends, as after a regression.
        val recorded = fixture()
        val broken = recorded.replace("\"const\": 42.5", "\"const\": 49.99")
        check(broken != recorded) { "the fixture no longer records purchase.value as 42.5" }

        val purchase = Inspector.entriesSnapshot.filterIsInstance<AnalyticsEntry>().single { it.name == "purchase" }
        assertEquals(
            """
            Baseline: 3 expected, 2 matched, 1 missing, 1 extra
            ✗ missing  purchase · firebase, #2 in the baseline
                closest: event ${purchase.id}
                  value · Not the expected value · expects 49.99
            + extra    purchase · firebase, event ${purchase.id}
            """.trimIndent(),
            Baseline.diff(broken, variables = variables),
        )
    }

    private fun runFlow() {
        listOf("Log screen_view", "Log purchase (ecommerce)", "Log server-side (gtm)").forEach {
            compose.onNodeWithText(it).performScrollTo().performClick()
        }
        compose.waitForIdle()
    }

    private fun fixture(): String =
        checkNotNull(javaClass.getResource("/$FIXTURE")) { "$FIXTURE is missing from the test resources" }.readText()

    private companion object {
        const val FIXTURE = "sample-flow.baseline.json"
    }
}
