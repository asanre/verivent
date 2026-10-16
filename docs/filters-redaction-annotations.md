# Filters, redaction & annotations

All configuration is plain Kotlin predicates — no annotations, no codegen.

## Network (`install(Snoop) { ... }`)

| Option | Type | Effect |
|---|---|---|
| `maxContentLength` | `Long` (default 250_000) | Bodies larger than this are captured truncated, flagged in the UI. |
| `sanitizeHeader { name -> Bool }` | additive | Redact matching request/response headers. `Authorization`, `Cookie`, `Set-Cookie` are always redacted. |
| `sanitizeQueryParameter { name -> Bool }` | additive | Redact matching query parameters **and form-body fields**. A default list of secret-ish names is always redacted. |
| `sanitizeBody { text -> String }` | additive | Rewrite a captured body preview. Applied after the form-field redaction below. |
| `filter { request -> Bool }` | additive | Keep a request only if **every** filter returns true. A `false` skips capture entirely. |

```kotlin
install(Snoop) {
    maxContentLength = 500_000
    sanitizeHeader { it.equals("X-Api-Key", ignoreCase = true) }
    filter { it.url.encodedPath != "/health" }
}
```

## Network — native iOS (`Snoop.installNetworkInterceptor`)

The `URLSession` counterpart of the table above, for hosts on the Swift Package. Same engine, same
defaults; the differences are that every rule is a single closure rather than an additive list, and
that **each call replaces the previous configuration** instead of adding to it.

| Option | Type | Ktor counterpart |
|---|---|---|
| `maxContentLength` | `Int64` (default 250_000) | `maxContentLength` |
| `redactHeader` | `((String) -> Bool)?` | `sanitizeHeader` |
| `redactQueryParameter` | `((String) -> Bool)?` | `sanitizeQueryParameter` |
| `redactBody` | `((String) -> String)?` | `sanitizeBody` |
| `keepRequest` | `((Snoop.Request) -> Bool)?` | `filter` |

```swift
let configuration = URLSessionConfiguration.default
Snoop.installNetworkInterceptor(
    on: configuration,
    redactHeader: { $0.lowercased() == "x-api-key" },
    keepRequest: { $0.path != "/health" }
)
```

Experimental, and partial by nature — see [Native iOS](native-ios.md#capture-network-traffic) for
what `URLProtocol` cannot reach.

## Analytics (`SnoopAnalytics { ... }`)

| Option | Type | Effect |
|---|---|---|
| `filter { event -> Bool }` | additive | Keep an event only if every filter returns true. |
| `sanitizeProperty { key -> Bool }` | additive | Redact matching property values, in the event and in every extra parameter block. |
| `annotate { event -> List<Annotation> }` | additive | Attach semantic badges. |
| `groupBy(event, labelProperty)` | replaces | Which event opens a collapsible timeline group, and which property labels it. Defaults to `"screen_view"` / `"screen_name"`. |

`Annotation` values: `Ecommerce`, `Conversion`, `Consent`, `ScreenView`.

```kotlin
val snoop = SnoopAnalytics {
    filter { !it.name.startsWith("debug_") }
    sanitizeProperty { it in setOf("user_email", "user_id") }
    annotate {
        buildList {
            if (it.name in ecommerceEvents) add(Annotation.Ecommerce)
            if (it.name == "purchase") add(Annotation.Conversion)
        }
    }
    groupBy("page_shown", "page")   // or groupBy(null) for no grouping at all
}
```

### Grouping

The timeline collapses into sections: the event named in `groupBy` opens one, labelled with its
`labelProperty` value, and everything logged until the next one belongs to it. Out of the box that
is Firebase's manual screen event — `screen_view`, labelled with `screen_name` — so grouping works
without calling `groupBy` at all. When the event doesn't carry the property, the group takes the
event's own name; when the property is redacted, the label is the `██` placeholder.

The decision is made **once, as the event is captured**, and every viewer (embedded `SnoopScreen`,
the Android Activity, the web page, the native iOS hosts) groups by that stamp. Two consequences
worth knowing:

- Reconfiguring later only affects events captured from then on — it does not regroup the buffer.
- Each matching event opens a *new* group, so revisiting a screen shows up as a second section
  rather than merging into the first.

Grouping only exists where events are captured: the viewers group the timeline as soon as an event
carries a group label, and show one flat list otherwise. There is no switch to turn it off.

## Redaction

Redacted values are replaced with a visible `██` placeholder, so a reviewer can confirm the field
was sent without exposing its value. Redaction happens at capture time — raw secrets never enter
the in-memory store, and the request that actually goes out is never touched.

Three things are redacted:

- **Headers** — the defaults (`authorization`, `cookie`, `set-cookie`) plus your `sanitizeHeader`
  rules, matched against the name as sent and its lowercase form.
- **Query parameters** — a built-in list of secret-ish names (`token`, `access_token`, `api_key`,
  `password`, `client_secret`, `signature`, …) plus your `sanitizeQueryParameter` rules. The URL
  fragment goes through the same rules, since an OAuth implicit-flow redirect hands the token back
  as `#access_token=…`; a fragment that isn't a parameter list (`#section`) is left alone.
- **Form bodies** — a request body sent as `application/x-www-form-urlencoded` is split on
  `name=value&…` and run through the *same* rules as query parameters, so `POST /oauth/token` with
  `password=…&client_secret=…` is stored as `password=██&client_secret=██`. Names are matched as
  sent: a percent-encoded name (`client%5Fsecret`) doesn't match. Requests only.

A predicate that throws counts as a match, so a broken rule fails closed and redacts.

## Buffer size

The store keeps the most recent entries (default 500) in a ring buffer. Change it before traffic
starts:

```kotlin
SnoopCore.configure(capacity = 1000)
```
