# Testing analytics

Three ways to put analytics under test: compare a test's session with a recorded **baseline**, from
Kotlin; the same baselines from an iOS UI test, with `VeriventUITesting`; or ask the device's web
server whether any event broke the plan, from anything that speaks HTTP.

## Baseline

A baseline is the analytics a flow of the app must send, kept as a file next to the test and
reviewed like code. The test runs the flow inside `flow { }`; Verivent waits for the events and
compares them with the file:

```kotlin
@Test
fun checkoutSendsItsAnalytics() = runAndroidComposeUiTest<MainActivity> {
    Inspector.reset()
    Baseline.parse(readResource("baselines/checkout.json")).flow(waiter()) {
        tap("catalog.item.SN-42")
        tap("product.addToCart")
        tap("cart.checkout")
        tap("payment.pay")
        val orderId by bound(orderIdOnScreen())
    }                                                    // throws BaselineMismatch when they don't match
}
```

### The file

Each expected event is the tracking plan's JSON Schema for its properties. A recording writes
every property as a `const` of the value sent:

```json
{ "flow": "checkout", "order": "set",
  "events": [
    { "name": "purchase", "channel": "firebase",
      "schema": { "required": ["transaction_id", "value", "currency", "user_email", "items"],
                  "properties": { "transaction_id": { "const": "{{orderId}}" },
                                  "value":      { "const": 39.9 },
                                  "currency":   { "const": "EUR" },
                                  "user_email": { "type": "string" },
                                  "items":      { "type": "array", "minItems": 1,
                                                  "items": { "type": "object",
                                                             "properties": { "item_id": { "type": "string" } } } } },
                  "additionalProperties": false } } ] }
```

Then polish it like a plan:

- **A value you don't care about:** replace the `const` with a `type`, a `pattern` or nothing.
- **A value that changes per run:** bind it in the test (see [below](#values-that-change-per-run)).
- **Lists** are recorded by their shape, because their items vary from run to run.
- **Redacted properties** are recorded with their type only, and only their type is compared.

How it compares:

- Every listed event must be sent exactly as many times as it's listed, with no issue against its
  schema. One never sent is **missing**; one sent again, a second firing say, is **sent more times
  than expected**.
- Events of a name and channel the baseline doesn't list are ignored: the plan covers those.
- `"order": "set"` (the default) ignores the order. `"sequence"` also wants it.

### Steps

A longer flow splits into steps, so a failure says where it happened and an event sent at two
points of the flow is expected at each:

```kotlin
baseline.flow(waiter()) {
    step("browse") { tap("catalog.item.SN-42") }
    step("buy") {
        tap("product.addToCart")
        tap("cart.checkout")
    }
    step("pay") { tap("payment.pay") }
}
```

The file then lists `steps`, each with its own `order` and `events`, and the test runs them in
that order; a step it skips fails the flow. Each step is compared once its action returns, with the
events sent since it started. When the flow ends, an event sent more often over the whole flow than
its steps list it fails too, even if it arrived in a later step that doesn't list it. `step`
returns what its action returns.

### Values that change per run

`bound` names a value only the test knows, after the property it's delegated to:

```kotlin
step("pay") {
    tap("payment.pay")
    val orderId by bound(orderIdOnScreen())
}
```

Recording writes `"{{orderId}}"` wherever that value was sent, and for a list a `contains` with the
first item holding it:

```json
"items": { "type": "array", "minItems": 1, "items": { "type": "object" },
           "contains": { "properties": { "item_id": { "const": "{{itemId}}" } }, "required": ["item_id"] } }
```

Comparing fills it in. A string that is only the variable takes its value and type
(`"{{quantity}}"` bound to `2` is the integer 2); inside a longer string it's interpolated. A
variable with no value throws, and so does recording two variables bound to the same value. A
failure names the variable, `expects {{orderId}}`, not the value.

### Events sent in batches

An event the app sends later, such as a list flushed when the screen closes, is marked
`"deferred": true`. It's compared when the flow ends, against everything sent since its step
started, instead of when its step ends.

### Waiting

`flow` waits up to `timeout` (5 seconds) for each step's events, on a `Waiter`. The default,
`Waiter.RealTime`, polls in real time: right for UI Automator and plain instrumentation. Compose's
test clock only moves while the test waits on it, so a Compose test passes its own:

```kotlin
fun ComposeUiTest.waiter() = Waiter { timeout, condition ->
    try {
        waitUntil(timeoutMillis = timeout.inWholeMilliseconds, condition = condition)
        true
    } catch (_: ComposeTimeoutException) {
        false
    }
}
```

### Recording

The same test records the file: build the baseline with `Baseline.record("checkout")` instead of
`parse`, run the flow, and take `toJson()`. Each step is recorded once nothing new has arrived for
half a second. On Android, write it to the test output directory, which Gradle copies off the
device into `build/outputs/connected_android_test_additional_output/`:

```kotlin
val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
File(output, "${baseline.name}.json").writeText(baseline.toJson())
```

Then polish it and approve it in the pull request: it's the source of truth for that flow. A file
from Verivent 0.9 (with `"baseline": 1`) is rejected: record it again.

### The report

A failure names the flow, the step and each event, with the plan's messages by property path, and
never quotes a value sent:

```text
checkout › pay
  ✕ purchase · firebase
      value · Not the expected value · expects 49.99
  ✕ screen_view · firebase  missing
```

`BaselineMismatch.diffs` carries the same as data: a `BaselineDiff` per failing step, with
`Finding.Missing`, `Extra`, `Mismatch` (and its `PlanIssue`s), `OutOfOrder` and `StepNotRun`.

A step can expect the same event several times, one per [variant](integration-analytics-facade.md#variants)
of a generic event: each expected event fixes the values that tell them apart, so each pairs only
with a send of its own variant. With the plan loaded, the findings say which variant, by its id
(`variant`, and `beforeVariant` on `OutOfOrder`), and a send of another variant never explains the
one missing:

```text
checkout › share
  ✕ ui_interaction · firebase › share_playlist  missing
  ✕ ui_interaction · firebase › like_track  sent more times than expected
```

Without a plan, the variant is null and the closest send explains a missing one, as for any event.

### One event at a time

For a check that needs no baseline:

```kotlin
lastEvent("checkout_started" on "gtm").shouldMatchPlan()
lastEvent("purchase" on "firebase").shouldHave("currency", "EUR")
```

`lastEvent` waits for the event like `flow` does. `shouldMatchPlan` asks the tracking plan loaded
now; `shouldHave` compares as JSON (`3` equals `3.0`, not `"3"`) and refuses a redacted property.

For a generic event, the latest send overall may be another variant fired right after the one under
test. Pick it by variant, as the plan loaded at capture told it, or by the values it was sent with:

```kotlin
lastEvent("ui_interaction" on "firebase", variant = "share_playlist").shouldMatchPlan()
lastEvent("ui_interaction" on "firebase", where = mapOf("element_id" to "playlist_share")).shouldHave("item_id", "pl_42")
```

`variant` needs a plan that defines it, and throws on an id the plan doesn't have; `where` compares
like `shouldHave` and refuses a redacted property.

### In the sample

`sample/composeApp` checks its own analytics on a device, in `CheckoutAnalyticsTest`: buying the
desk lamp in one go and step by step (`browse`, `buy`, `pay`, with the order id bound), a baseline
that expects a price the app no longer charges, and the single-event checks. The baselines are in
`sample/shared/baselines`, which the SwiftUI sample checks too.

```bash
./gradlew :sample:composeApp:connectedDebugAndroidTest   # compare
./gradlew :sample:composeApp:recordBaselines             # record them again, into sample/shared/baselines
```

### With the no-op

The baseline lives in the `verivent` module. With `verivent-no-op`, `flow` and `lastEvent` always
fail with *Verivent no-op: nothing was captured*, so a test wired to the no-op fails instead of
passing on an empty session.

## On iOS

A UI test runs in a process of its own, so it checks the app's analytics through the
`VeriventUITesting` product of the Swift package: the same baseline files, steps, bound values and
report as the Kotlin API. Add the product to your UI test target only; the app needs nothing but
Verivent itself.

```swift
import VeriventUITesting
import XCTest

final class CheckoutAnalyticsTests: XCTestCase {

    func testCheckoutSendsItsAnalytics() throws {
        let app = XCUIApplication()
        let verivent = try VeriventApp.launch(app)              // instead of app.launch()

        try verivent.flow("checkout_steps") { flow in           // checkout_steps.json in the test bundle
            try flow.step("browse") { app.buttons["catalog.item.SN-42"].tap() }
            try flow.step("buy") {
                app.buttons["product.addToCart"].tap()
                app.buttons["cart.checkout"].tap()
            }
            try flow.step("pay") {
                app.buttons["payment.pay"].tap()
                flow.bind("orderId", app.staticTexts["confirmation.orderId"].label)
            }
        }                                                       // throws BaselineMismatch with the report

        try verivent.lastEvent("purchase", on: "firebase").shouldMatchPlan().shouldHave("currency", "EUR")
    }
}
```

`VeriventApp.launch` passes a token and a port in the launch environment; the app starts Verivent's
server on its loopback, with that token, the first time it calls Verivent, and the test talks to it
over the simulator's localhost, which the test runner shares. Nothing else changes in the app: its
first Verivent call, whichever it is, starts the test's server, and a `startWebViewer` of its own
then gets that one.

`flow` waits up to `timeout` (5 seconds) for each step, as the Kotlin one does, and `bind` takes the
place of `bound`: bind a value before the step that sends it ends. `lastEvent` picks a generic
event's variant the same way: `verivent.lastEvent("ui_interaction", on: "firebase", variant: "share_playlist")`,
or `where: ["element_id": "playlist_share"]`.

To record, run the tests with `VERIVENT_RECORD_DIR` in the test runner's environment; each flow writes
`<name>.json` there instead of comparing:

```bash
TEST_RUNNER_VERIVENT_RECORD_DIR="$PWD/baselines" xcodebuild test -scheme MyApp -destination "id=$SIM"
```

A recording writes every event's properties in alphabetical order, so iOS records the same file
Android does and one baseline can serve both apps. The samples do
just that: `sample/iosApp`'s `CheckoutAnalyticsTests` checks the files in `sample/shared/baselines`
that the Compose sample's test records.

### The routes underneath

`VeriventUITesting` is a client of these routes on the app's server, behind the same gate as the
rest; a test in any other language can drive them the same way. Each `POST` answers
`{"passed": true}`, or `{"passed": false, "report": "…"}` with the Kotlin failure's text, and a
misuse, such as a step out of order, answers `400` with the reason. A flow that fails is over.

| Route | Does |
|---|---|
| `POST /api/baseline/flow` | Starts a flow against the baseline in the body; `?record=<name>` records one instead. `?timeout=` in ms |
| `POST /api/baseline/step?name=` | Starts the next step |
| `POST /api/baseline/step/end` | Waits for the step's events and checks them, or records them |
| `POST /api/baseline/finish` | Ends the flow; a recording comes back as `recording` |
| `POST /api/baseline/last` | Checks the latest event: `{"name", "channel", "plan": true}` or `{"name", "channel", "property", "value"}`, of a variant with `"variant"` or sent with the values in `"where": {…}` |

`step/end` and `finish` take the values bound so far as `{"variables": {"orderId": "T-1"}}`.

## Over HTTP

The web viewer's server also answers whether any event broke the plan, so a black-box test
(Maestro, Appium, a shell script in CI) can check analytics without linking any Verivent code.
Start the server in the build under test with a token you know, and load a plan:

```kotlin
if (BuildConfig.DEBUG) WebViewer.start(token = "ci")
```

```bash
adb forward tcp:9394 tcp:9394                                  # Android; the iOS simulator shares localhost
curl -s -X POST -H 'X-Verivent: 1' --data-binary @plan.json 'http://localhost:9394/api/tracking-plan?t=ci'
curl -s -X POST -H 'X-Verivent: 1' 'http://localhost:9394/api/clear?t=ci'      # reset between tests
# … drive the app …
curl -s 'http://localhost:9394/api/assert/all?t=ci'
```

```json
{ "plan": "1.4.0", "total": 12, "good": 10, "bad": 1, "unplanned": 1 }
```

| Route | Answers |
|---|---|
| `GET /api/assert/all` | How many analytics events, by verdict, against the plan loaded now |
| `GET /api/assert/good` | Events the loaded plan checked and found compliant |
| `GET /api/assert/bad` | Events the loaded plan checked, with their `issues` |
| `GET /api/assert/unplanned` | Events the loaded plan didn't check: it doesn't define them, or there's no plan |

The three lists take `?name=` and `?channel=` filters and return the events as the
[export](integration-analytics-facade.md#exported-sessions) writes them, without its opening
`source` entry. Every route sits behind the same
gate as the viewer: the token in `?t=`, and `X-Verivent: 1` on the requests that change something.
`POST /api/clear` resets the captured events.

A test that only needs "no event broke the plan" asserts that `bad` is 0. One that needs to know
that an event was sent at all asks for it by name in `good`.
