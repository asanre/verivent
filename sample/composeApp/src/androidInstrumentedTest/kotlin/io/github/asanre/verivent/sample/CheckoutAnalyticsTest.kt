package io.github.asanre.verivent.sample

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Assume.assumeFalse
import org.junit.Test

/**
 * The shop's analytics, sent by buying a desk lamp and checked against the baselines in
 * `sample/shared/baselines`, which the SwiftUI sample's UI tests check too.
 * `./gradlew :sample:composeApp:recordBaselines` records them again.
 */
@OptIn(ExperimentalTestApi::class)
class CheckoutAnalyticsTest {

    @Test
    fun checkoutSendsItsAnalytics() = sampleTest {
        val baseline = baseline("checkout")

        baseline.flow(waiter()) {
            buyTheDeskLamp()
            val orderId by bound(orderId())
        }

        keep(baseline)
    }

    @Test
    fun checkoutSendsItsAnalyticsStepByStep() = sampleTest {
        val baseline = baseline("checkout_steps")

        baseline.flow(waiter()) {
            step("browse") { tap("catalog.item.SN-42") }
            step("buy") {
                tap("product.addToCart")
                tap("cart.checkout")
            }
            step("pay") {
                tap("payment.pay")
                val orderId by bound(orderId())
            }
        }

        keep(baseline)
    }

    @Test
    fun aBrokenParameterNamesItsEventsAndPath() = sampleTest {
        assumeFalse("compares a recorded baseline", recording)
        // A baseline expecting a price the app no longer charges, as after a regression.
        val recorded = resource("checkout")
        val edited = recorded.replace("\"const\": 39.9", "\"const\": 49.99")
        check(edited != recorded) { "the checkout baseline no longer records the lamp's 39.9" }

        val failure = assertThrows(BaselineMismatch::class.java) {
            Baseline.parse(edited).flow(waiter(), timeout = 1.seconds) {
                buyTheDeskLamp()
                val orderId by bound(orderId())
            }
        }

        val wrongValue = listOf(PlanIssue("value", "Not the expected value · expects 49.99"))
        assertEquals(
            listOf("view_item", "add_to_cart", "begin_checkout", "purchase").map { Finding.Mismatch(it on "firebase", wrongValue) },
            failure.diffs.single().findings,
        )
    }

    @Test
    fun oneEventCheckedOnItsOwn() = sampleTest {
        tap("catalog.developer")
        tap("developer.loadPlan")
        tap("nav.back")
        buyTheDeskLamp()

        lastEvent("checkout_started" on "gtm", waiter()).shouldMatchPlan()
        lastEvent("purchase" on "firebase", waiter()).shouldMatchPlan().shouldHave("currency", "EUR")
    }

    /** The sample, launched and with nothing captured: what it sent on launch is cleared. */
    private fun sampleTest(block: ComposeUiTest.() -> Unit) = runAndroidComposeUiTest<MainActivity> {
        waitForIdle()
        Inspector.reset()
        block()
    }

    private fun ComposeUiTest.buyTheDeskLamp() {
        tap("catalog.item.SN-42")
        tap("product.addToCart")
        tap("cart.checkout")
        tap("payment.pay")
    }

    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun ComposeUiTest.orderId(): String =
        onNodeWithTag("confirmation.orderId").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString()

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

    /** A recording goes to the test output, which `recordBaselines` copies to sample/shared/baselines. */
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
