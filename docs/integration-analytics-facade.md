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
VeriventEvent: {"type":"analytics","id":"2","ts":…,"name":"purchase","channel":"firebase","properties":[…],"source":{"platform":"android","appVersion":"2.3.0","veriventVersion":"0.10.1"}}
```

The line is the event as every export writes it, so redacted values stay redacted, plus the
`source` that a [session file](#exported-sessions) states once: a console has no header to keep it
in. A line longer than the console's limit (about 3.8 KB on Android, 900 bytes on iOS) is
split into pieces prefixed `[<id> <i>/<n>] `; join them in order to get the JSON back.

To turn those lines into a report for a pull request, give them to an agent with the
[`analytics-events` skill](agent-skills.md).

## Exported sessions

*Export* in the inspector and the web viewer's download write the same file: a JSON array that
opens with the session's source, then its analytics events. The console lines carry the same
events, one per line. Network calls stay in the live timeline and aren't exported.

```json
[
  { "type": "source", "id": "source", "ts": 1761200000000,
    "source": { "platform": "android", "appVersion": "2.3.0", "veriventVersion": "0.10.1" } },
  { "type": "analytics", "id": "7", "ts": 1760900000000, "name": "play", "channel": "firebase",
    "properties": [
      { "name": "count",  "value": "3",              "redacted": false, "type": "integer" },
      { "name": "tracks", "value": "[{\"ms\":1200}]", "redacted": false, "type": "array" },
      { "name": "email",  "value": "██",             "redacted": true,  "type": "string" } ],
    "planVersion": "1.4.0" }
]
```

An event the plan tells apart by [variants](#variants) also carries `"variant"`, the id of the one it
was sent as. Readers from before 0.11 ignore it.

The opener says where the session was captured: `platform` is `android` or `ios`, `appVersion` is
the app's `versionName` on Android and its `CFBundleShortVersionString` on iOS (left out when the app
declares none), and `veriventVersion` is the library's. Its `ts` is when the file was written. It
carries an `id` and a `ts` like any entry, so readers that only look at `"type": "analytics"` skip
it. On Android the core reads the app's version through a ContentProvider of its own, which the
manifest merger adds to the debug build; `tools:node="remove"` on
`io.github.asanre.verivent.core.internal.VeriventInitProvider` takes it out, and the version with it.
The provider runs in the app's main process only, so events captured in another process leave the
version out too.

Each property says its JSON type, taken before redaction, so a session can be checked again against
any plan without false type errors. `value` is text: a string as sent, anything else as JSON. A
redacted property keeps only its type. Exports from before 0.10.1 have no opener, and those from
before 0.9 have no `type`. Exports from before 0.10 can also hold network calls
(`"type": "network"`), which the [npm validator](validator-npm.md) skips.

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

- Supported keywords: `type` (one or a list), `enum`, `const`, `pattern`, `properties`, `required`,
  `additionalProperties` (true when absent, as in JSON Schema), `items` (one schema every element
  of a list must meet), `minItems` and `contains` (at least one element meets it). They nest as
  deep as your values do. Others, such as `contentMediaType`, are ignored. `pattern` only applies
  to strings; integers satisfy `number`, and whole numbers satisfy `integer`.
- Lists and maps are checked inside. An issue names its path, with `.` into a map and `[*]` into
  every element of a list, and says how many elements break it:

    ```json
    "tracks": { "type": "array", "minItems": 1,
                "items": { "type": "object", "required": ["id"],
                           "properties": { "id": { "type": "string" },
                                           "duration_ms": { "type": "integer" } } } }
    ```

    Sending 60 tracks whose `duration_ms` is a string gives one issue,
    `tracks[*].duration_ms · Expected integer, got string · 60 of 60`. A list sent as JSON text is
    a string: *Expected array, got string*. Values nested past 64 levels aren't checked, and an
    event converts at most 10,000 lists and maps: the rest get *Too many lists and maps to check ·
    over 10,000 per event* and are shown and exported as `…`, which the npm validator sees as a string.
- `pattern` isn't anchored, as in JSON Schema: `[0-9]+` accepts `"sku-42"` because it finds digits
  somewhere in it. Write `^[0-9]+$` to constrain the whole value.
- A string over 256 characters isn't run through `pattern`: it gets the issue *Too long to check
  against the pattern · over 256 characters* instead. The check runs on your thread for every event, so this caps the
  cost of a pattern that backtracks. It can't stop one that backtracks exponentially, such as
  `(a+)+$`, on shorter values: avoid nesting quantifiers.
- The check runs on the values you pass to `log`, before redaction, so types are the host's types.
  A property you redact with `sanitizeProperty` is only checked for `required` and `type`, all the
  way down: a verdict on its `enum`, `const`, `pattern`, `minItems` or `contains` would give its
  value away.
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

### Variants

A generic event is the same event, by name and channel, sent in several places or ways and told
apart by the value of one of its properties: `ui_interaction` with an `element_id` for each element
touched, `share` by its `method`, `select_content` by its `content_type`. Give it `variants` next to
its `schema`, and each one says what its sends must hold on top of what every send holds:

```json
{ "name": "ui_interaction", "channel": "firebase",
  "schema": { "required": ["element_id", "action", "item_id"],
              "properties": { "element_id": { "type": "string" }, "action": { "type": "string" },
                              "item_id": { "type": "string" }, "position": { "type": "integer" } } },
  "variants": {
    "by": ["element_id"],
    "list": [
      { "id": "share_playlist", "description": "Share button on a playlist",
        "when": { "element_id": "playlist_share" },
        "properties": { "action": { "const": "share" }, "item_id": { "pattern": "^pl_" } } },
      { "id": "home_banner", "when": { "element_id": "home_hero_banner" },
        "required": ["position"],
        "properties": { "item_id": { "pattern": "^bnr_" } } } ] } }
```

These are the variants of one event in the tracking plan, not the variants of an A/B test: a
parameter such as `experiment_variant` is a property like any other.

- Every variant sends the same properties, as Firebase asks of events with the same name. A
  variant only narrows them: `properties` takes `const`, `enum` and `pattern` for a property the
  schema already has, and `required` makes one it already has required. It never adds one.
- `by` names the properties that tell variants apart, one or several. Each must be in the schema's
  `properties` and `required`, so a send without it says *Required, but not sent*. Every `when` sets
  a plain value for exactly those properties, and no two variants share a `when`.
- `id` names the variant in messages, in the inspector and in exports. A variant's other keys, such
  as `description`, are ignored. The plan is rejected, with a message naming the event and the
  variant, for anything else: a property the schema lacks, a keyword other than `const`, `enum` or
  `pattern`, a `by` that isn't required, two variants with the same `id` or `when`.
- A send is checked against the schema first, then against its variant. What breaks the variant
  says which one by its id, never by the value sent: `item_id · For share_playlist: Doesn't match the
  pattern ^pl_`. A property the schema already rejects isn't reported again.
- A send no variant fits gets one issue on the property that tells them apart, listing what the
  plan expects: `element_id · Not a planned variant · expects playlist_share or home_hero_banner`.
  With several properties in `by` it goes on the first: `element_id · Not a planned variant of
  element_id + action · expects playlist_share + share or …`.
- When a property in `by` is redacted, picking a variant would reveal its value: only the schema is
  checked, and the send gets `element_id · Redacted: can't tell which variant, only the common part
  was checked`.
- The event carries `variant`, the id of the one it was sent as, null when none fits. The inspector
  and the web viewer count coverage per variant, plus a row for sends no variant fits.
- An event without `variants` is checked as before, and a plan can mix both. Verivent before 0.11
  ignores `variants` and only checks the schema: it misses what a variant narrows, but never reports
  an issue the plan doesn't have.

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

