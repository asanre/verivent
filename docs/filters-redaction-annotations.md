# Filters, redaction & annotations

All configuration is plain Kotlin predicates — no annotations, no codegen.

## Network (`install(Snoop) { ... }`)

| Option | Type | Effect |
|---|---|---|
| `maxContentLength` | `Long` (default 250_000) | Bodies larger than this are captured truncated, flagged in the UI. |
| `sanitizeHeader { name -> Bool }` | additive | Redact matching request/response headers. `Authorization`, `Cookie`, `Set-Cookie` are always redacted. |
| `filter { request -> Bool }` | additive | Keep a request only if **every** filter returns true. A `false` skips capture entirely. |

```kotlin
install(Snoop) {
    maxContentLength = 500_000
    sanitizeHeader { it.equals("X-Api-Key", ignoreCase = true) }
    filter { it.url.encodedPath != "/health" }
}
```

## Analytics (`SnoopAnalytics { ... }`)

| Option | Type | Effect |
|---|---|---|
| `filter { event -> Bool }` | additive | Keep an event only if every filter returns true. |
| `sanitizeProperty { key -> Bool }` | additive | Redact matching property values. |
| `annotate { event -> List<Annotation> }` | additive | Attach semantic badges. |

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
}
```

## Redaction

Redacted values are replaced with a visible `██` placeholder, so a reviewer can confirm the field
was sent without exposing its value. Redaction happens at capture time — raw secrets never enter
the in-memory store.

## Buffer size

The store keeps the most recent entries (default 500) in a ring buffer. Change it before traffic
starts:

```kotlin
SnoopCore.configure(capacity = 1000)
```
