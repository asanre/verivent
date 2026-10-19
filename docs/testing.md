# Testing analytics

Two ways to put analytics under test: compare a test's session with a recorded **baseline**, from
Kotlin, or ask the device's web server whether any event broke the plan, from anything that speaks
HTTP.

## Baseline

Record what a flow sends once, keep the JSON next to the test, and compare every later run with it:

```kotlin
@Test
fun checkoutSendsItsAnalytics() {
    Inspector.reset()
    runCheckout()                                        // drive the app however your tests do

    // First run: print it and save it as checkout.baseline.json
    // println(Baseline.record())

    Baseline.diff(readResource("checkout.baseline.json"), variables = mapOf("order_id" to order.id))
        ?.let { fail(it) }
}
```

`Baseline.record()` writes every analytics event captured so far, in order, with the tracking
plan's JSON Schema. Each property is a `const` of the value sent:

```json
{ "baseline": 1, "order": "set", "extra": "fail",
  "events": [
    { "name": "add_to_cart", "channel": "firebase",
      "schema": { "required": ["item_id", "quantity", "email"],
                  "properties": { "item_id":  { "const": "42" },
                                  "quantity": { "const": 2 },
                                  "email":    { "type": "string" },
                                  "items":    { "type": "array", "minItems": 1,
                                                "items": { "type": "object",
                                                           "properties": { "id": { "type": "string" } } } } },
                  "additionalProperties": false } } ] }
```

Then edit it like a plan:

- **A value that changes per run:** write `"{{name}}"` in its `const` and pass it in `variables`.
  A string that is only the variable takes the variable's value and type (`"{{quantity}}"` with
  `2` is the integer 2); inside a longer string it's interpolated. A variable without a value
  throws.
- **A value you don't care about:** replace the `const` with a `type`, a `pattern` or nothing.
- **Lists:** they're recorded by shape, because their items vary from run to run. Add `contains`
  for the item the test knows:
  `"contains": { "properties": { "id": { "const": "{{item_id}}" } }, "required": ["id"] }`.
- **Redacted properties** are recorded with their type only, and only their type is compared.

How it compares:

- `"order": "set"` (the default) ignores the order. `"sequence"` also wants the recorded order.
- Every recorded event must be matched by one sent event: the same name and channel, and no
  issue. A recorded event that nothing matches is **missing**.
- `"extra": "fail"` (the default) also fails on any sent event the baseline doesn't account for,
  such as an event fired twice. `"allow"` lets them through.

`diff` returns null when the session matches, or a readable report. It names what the baseline
expected, never the values sent:

```text
Baseline: 3 expected, 1 matched, 2 missing, 2 extra
✗ missing  add_to_cart · firebase, #2 in the baseline
    closest: event 2
      quantity · Not the expected value · expects 2
✗ missing  purchase · firebase, #3 in the baseline
    closest: event 3
      items[*].qty · Expected integer, got string · 1 of 1
+ extra    add_to_cart · firebase, event 2
+ extra    purchase · firebase, event 3
```

The baseline lives in the `verivent` module. With `verivent-no-op`, `diff` always reports
*Verivent no-op: nothing was captured*, so a test wired to the no-op fails instead of passing on
an empty session. On iOS, check analytics over HTTP (below).
