package io.github.asanre.verivent.sample

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runAndroidComposeUiTest
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asanre.verivent.baseline.Baseline
import io.github.asanre.verivent.baseline.BaselineDiff.Finding
import io.github.asanre.verivent.baseline.BaselineMismatch
import io.github.asanre.verivent.baseline.Waiter
import io.github.asanre.verivent.baseline.lastEvent
import io.github.asanre.verivent.baseline.on
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.PlanIssue
import java.io.File
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

/**
 * The sample's analytics, sent by tapping its buttons and checked against the baselines in
 * `resources/baselines`. `./gradlew :sample:composeApp:recordBaselines` records them again.
 */
@OptIn(ExperimentalTestApi::class)
class CheckoutAnalyticsTest {

    @Test
    fun checkoutSendsItsAnalytics() = sampleTest {
        val baseline = baseline("checkout")

        baseline.flow(waiter()) {
            tap("Log screen_view")
            tap("Log purchase (ecommerce)")
            tap("Log server-side (gtm)")
        }

        keep(baseline)
    }

    @Test
    fun checkoutSendsItsAnalyticsStepByStep() = sampleTest {
        val baseline = baseline("checkout_steps")

        baseline.flow(waiter()) {
            step("browse") { tap("Log screen_view") }
            step("buy") {
                // Recorded as {{itemId}}: the one item of the list the test knows.
                val itemId by bound("SN-42")
                tap("Log purchase (ecommerce)")
            }
            step("checkout") { tap("Log server-side (gtm)") }
        }

        keep(baseline)
    }

    @Test
    fun aBrokenParameterNamesItsEventAndPath() = sampleTest {
        assumeFalse("compares a recorded baseline", recording)
        // A baseline expecting a value the app no longer sends, as after a regression.
        val recorded = resource("checkout")
        val edited = recorded.replace("\"const\": 42.5", "\"const\": 49.99")
        check(edited != recorded) { "the checkout baseline no longer records purchase.value as 42.5" }
        val broken = Baseline.parse(edited)

        val failure = assertThrows(BaselineMismatch::class.java) {
            broken.flow(waiter(), timeout = 1.seconds) {
                tap("Log screen_view")
                tap("Log purchase (ecommerce)")
                tap("Log server-side (gtm)")
            }
        }

        assertEquals(
            listOf(Finding.Mismatch("purchase" on "firebase", listOf(PlanIssue("value", "Not the expected value · expects 49.99")))),
            failure.diffs.single().findings,
        )
        assertTrue(failure.message, "  ✕ purchase · firebase\n      value · Not the expected value · expects 49.99" in failure.message.orEmpty())
    }

    @Test
    fun oneEventCheckedOnItsOwn() = sampleTest {
        tap("Load sample tracking plan")
        tap("Log server-side (gtm)")
        tap("Log purchase (ecommerce)")

        lastEvent("checkout_started" on "gtm", waiter()).shouldMatchPlan()
        lastEvent("purchase" on "firebase", waiter()).shouldHave("currency", "EUR")
    }

    /** The sample, launched and with nothing captured: the app reloads what an earlier run left. */
    private fun sampleTest(block: ComposeUiTest.() -> Unit) = runAndroidComposeUiTest<MainActivity> {
        Inspector.reset()
        block()
    }

    private fun ComposeUiTest.tap(button: String) {
        onNodeWithText(button).performScrollTo().performClick()
    }

    /** Waits on Compose's test clock, which only moves while the test waits on it. */
    private fun ComposeUiTest.waiter() = Waiter { timeout, condition ->
        try {
            waitUntil(timeoutMillis = timeout.inWholeMilliseconds, condition = condition)
            true
        } catch (_: ComposeTimeoutException) {
            false
        }
    }

    private fun baseline(name: String): Baseline = if (recording) Baseline.record(name) else Baseline.parse(resource(name))

    /** A recording goes to the test output, which `recordBaselines` copies next to this test. */
    private fun keep(baseline: Baseline) {
        if (!baseline.isRecording) return
        val output = checkNotNull(arguments.getString("additionalTestOutputDir")) { "the runner has no test output directory" }
        File(output, "${baseline.name}.json").apply { parentFile?.mkdirs() }.writeText(baseline.toJson())
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResource("/baselines/$name.json")) { "baselines/$name.json is missing: run recordBaselines" }.readText()

    private val arguments get() = InstrumentationRegistry.getArguments()

    private val recording get() = arguments.getString("recordBaselines") == "true"
}
