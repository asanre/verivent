# Testing analytics

Two ways to put analytics under test: compare a test's session with a recorded **baseline**, from
Kotlin, or ask the device's web server whether any event broke the plan, from anything that speaks
HTTP.

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

### One event at a time

For a check that needs no baseline:

```kotlin
lastEvent("checkout_started" on "gtm").shouldMatchPlan()
lastEvent("purchase" on "firebase").shouldHave("currency", "EUR")
```

`lastEvent` waits for the event like `flow` does. `shouldMatchPlan` asks the tracking plan loaded
now; `shouldHave` compares as JSON (`3` equals `3.0`, not `"3"`) and refuses a redacted property.

### In the sample

`sample/composeApp` checks its own analytics on a device, in `CheckoutAnalyticsTest`: buying the
desk lamp in one go and step by step (`browse`, `buy`, `pay`, with the order id bound), a baseline
that expects a price the app no longer charges, and the single-event checks. The baselines are in
`sample/shared/baselines`, which the SwiftUI sample checks too.

```bash
./gradlew :sample:composeApp:connectedDebugAndroidTest   # compare
./gradlew :sample:composeApp:recordBaselines             # record them again, into sample/shared/baselines
```

### With the no-op and on iOS

The baseline lives in the `verivent` module. With `verivent-no-op`, `flow` and `lastEvent` always
fail with *Verivent no-op: nothing was captured*, so a test wired to the no-op fails instead of
passing on an empty session. On iOS, check analytics over HTTP (below).

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

The three lists take `?name=` and `?channel=` filters and return the same JSON as the
[export](integration-analytics-facade.md#exported-sessions). Every route sits behind the same
gate as the viewer: the token in `?t=`, and `X-Verivent: 1` on the requests that change something.
`POST /api/clear` resets the captured events.

A test that only needs "no event broke the plan" asserts that `bad` is 0. One that needs to know
that an event was sent at all asks for it by name in `good`.
