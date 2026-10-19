# Hook your analytics facade

Verivent can't see analytics events by inspecting network traffic — vendor SDKs like Firebase send over
their own opaque transport. The one place every event is visible is your app's **analytics facade**,
the single function all events flow through before they fan out to vendors. Wire Verivent there.

## The idea

If your app has a chokepoint like this:

```kotlin
interface AnalyticsHelper {
    fun log(event: AnalyticsEvent)
}

class BaseAnalyticsHelper(
    private val dispatchers: List<AnalyticsDispatcher>,   // Firebase, server-side, GTM, ...
) : AnalyticsHelper {
    override fun log(event: AnalyticsEvent) {
        dispatchers.forEach { it.dispatch(event) }        // <-- every event passes here
    }
}
```

wrap it with a decorator that also forwards to Verivent, gated to internal/debug builds:

```kotlin
class VeriventAnalyticsHelper(
    private val delegate: AnalyticsHelper,
    private val sink: AnalyticsSink = AnalyticsSink {
        sanitizeProperty { it in setOf("user_email", "user_id") }
        annotate { if (it.name in ecommerceEvents) listOf(Annotation.Ecommerce) else emptyList() }
    },
) : AnalyticsHelper {
    override fun log(event: AnalyticsEvent) {
        delegate.log(event)
        sink.log(
            name = event.name,
            channel = event.channelLabel(),      // map your sealed event type -> "firebase" / "gtm" / ...
            properties = event.properties,
        )
    }
}
```

Bind `VeriventAnalyticsHelper` in place of `BaseAnalyticsHelper` only in the internal build variant; the
production variant binds the plain helper and pulls the `verivent-no-op` artifact, so this
decorator can stay in shared code and compile to nothing in release.

## Channels

`channel` is a free-form string. Use it to separate transports (`"firebase"`, `"gtm"`,
`"server-side"`); the UI groups, filters and colors by it. Derive it from your own event type:

```kotlin
private fun AnalyticsEvent.channelLabel() = when (this) {
    is FirebaseEvent -> "firebase"
    is ServerSideEvent -> "server-side"
    is GtmEvent -> "gtm"
}
```

## Extra parameter blocks

Providers attach more than the event's own parameters to every hit: Firebase adds the default event
parameters and the current user properties, and DebugView shows them next to each event. Pass them
as `sections`, a map from block title to parameters, and the event detail shows one block per entry
below *Parameters*, in the order you give. Empty blocks are dropped. Verivent keeps no state of its
own, so take the snapshot where the event is sent:

```kotlin
sink.log(
    name = event.name,
    channel = "firebase",
    properties = event.properties,
    sections = mapOf(
        "Default parameters" to defaultEventParams.toMap(),
        "User properties" to currentUserProperties.toMap(),
    ),
)
```

`properties` stays the event itself: it is what `filter`, `annotate` and `groupBy` see, and what
*Raw* and *Copy payload* show. `sanitizeProperty` redacts every block too.

## Console lines

Turn on `logTo` and every captured event is also written as one JSON line, so whoever can't open
the inspector (an agent driving the app, a CI log) still sees what was sent:

```kotlin
val sink = AnalyticsSink {
    sanitizeProperty { it == "user_email" }
    logTo()                                   // Logcat (tag VeriventEvent) / unified log on iOS
    // logTo { Timber.tag("VeriventEvent").i(it) } to go through your own logging
}
```

```text
adb logcat -s VeriventEvent
VeriventEvent: {"type":"analytics","id":"2","ts":…,"name":"purchase","channel":"firebase","properties":[…]}
```

The line is the captured entry, the same JSON the web viewer receives, so redacted values stay
redacted. A line longer than the console's limit (about 3.8 KB on Android, 900 bytes on iOS) is
split into pieces prefixed `[<id> <i>/<n>] `; join them in order to get the JSON back.

## Tracking plan

Load the analytics team's tracking plan and Verivent checks every event captured from then on against
it. The plan defines each event per channel with a JSON Schema subset over its properties:

```json
{ "version": "1.4.0",
  "events": [
    { "name": "add_to_cart", "channel": "server-side",
      "schema": { "required": ["list_scope", "item_id"],
                  "properties": { "list_scope": { "enum": ["search", "catalog", "pdp"] },
                                  "item_id":    { "type": "string", "pattern": "^[0-9]+$" } },
                  "additionalProperties": false } } ] }
```

- Supported keywords: `required`, `properties` with `type` (one or a list), `enum` and `pattern`,
  and `additionalProperties`, true when absent as in JSON Schema. Others are ignored. `pattern`
  only applies to strings; integers satisfy `number`, and whole numbers satisfy `integer`.
- `pattern` isn't anchored, as in JSON Schema: `[0-9]+` accepts `"sku-42"` because it finds digits
  somewhere in it. Write `^[0-9]+$` to constrain the whole value.
- A string over 256 characters isn't run through `pattern`: it gets the issue *Too long to check
  against the pattern* instead. The check runs on your thread for every event, so this caps the
  cost of a pattern that backtracks. It can't stop one that backtracks exponentially, such as
  `(a+)+$`, on shorter values: avoid nesting quantifiers.
- The check runs on the values you pass to `log`, before redaction, so types are the host's types.
  A property you redact with `sanitizeProperty` is only checked for `required` and `type`: a verdict
  on its `enum` or `pattern` would give its value away.
  `sections` are not checked.
- An event the plan defines carries `planVersion`, the version it was checked against, and
  `issues`: one `PlanIssue(property, message)` per thing that breaks the plan, empty when it
  matches. Messages state what the plan expects, never the value sent. Both reach the inspector,
  the web viewer and console lines.
- An event the plan doesn't define isn't checked: no `planVersion`, no issues. A partial plan is
  expected while the catalog grows.
- Like grouping, the verdict is stamped at capture. Loading or clearing a plan doesn't revisit
  earlier events: they keep their `planVersion` and `issues` in the export and in console lines.
  The inspector and the web viewer only show the verdicts of the plan loaded now, so clearing or
  replacing it hides the earlier ones, and loading the same version again brings them back.

Load it whichever way suits who is testing:

| Who | How |
|---|---|
| Host code, tests | `Inspector.loadTrackingPlan(json)` / `clearTrackingPlan()`; throws `IllegalArgumentException` on an invalid plan and keeps the current one |
| Someone on the device (BrowserStack included) | the plan strip under the inspector's search, then *Paste from clipboard* or paste in the field |
| Someone on the web viewer | *No plan* / *Plan x.y* in the header: drop or pick a file, or paste it |
| An agent with `adb` | write the file below with `run-as`, then restart the app |

To keep the plan across launches, give Verivent a directory; setting it loads the plan stored there
in the background, off the calling thread:

```kotlin
Inspector.configure(storageDir = "${context.filesDir}/verivent")
```

```text
adb shell "run-as <your.package> sh -c 'mkdir -p files/verivent && cat > files/verivent/tracking-plan.json'" < plan.json
```

`Inspector.trackingPlan` is a `StateFlow` of the loaded plan's version and the events it defines,
null when none.

