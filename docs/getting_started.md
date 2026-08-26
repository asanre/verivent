# Getting started

## Install

Use the real artifacts in debug and the no-op mirrors in release, so release builds capture nothing
and drop the UI/body-retention overhead:

```kotlin
dependencies {
    debugImplementation("io.github.asanre:snoop-ktor:0.2.0")
    releaseImplementation("io.github.asanre:snoop-ktor-no-op:0.2.0")

    // the on-device inspector UI:
    debugImplementation("io.github.asanre:snoop-ui:0.2.0")
    releaseImplementation("io.github.asanre:snoop-ui-no-op:0.2.0")

    // optional, only if you also want analytics inspection:
    debugImplementation("io.github.asanre:snoop-analytics:0.2.0")
    releaseImplementation("io.github.asanre:snoop-analytics-no-op:0.2.0")
}
```

`snoop-core` (models, store, redaction and the web viewer) arrives transitively — you never declare
it. The no-op mirrors declare the exact same classes and functions, so your call sites compile
unchanged.

!!! tip "Skipping `snoop-ui`"
    `snoop-ui` is the only artifact that pulls in Compose. Leave it out if you inspect from the
    [web viewer](web-viewer.md) instead of on-device — everything else keeps working, and Compose
    never enters your dependency graph.

## Capture network traffic

Install the plugin on your Ktor client:

```kotlin
val client = HttpClient(engine) {
    install(Snoop) {
        maxContentLength = 250_000                              // truncate bodies larger than this
        sanitizeHeader { it == HttpHeaders.Authorization }      // Authorization/Cookie/Set-Cookie are redacted by default
        sanitizeQueryParameter { it == "token" }               // common secret params are redacted by default
        sanitizeBody { it.replace(emailRegex, "██") }          // rewrite the stored preview only
        filter { request -> request.url.host != "metrics.internal" }
    }
}
```

Every option is a plain predicate — see [Filters, redaction & annotations](filters-redaction-annotations.md)
for the full list.

## Capture analytics events

Snoop never talks to an analytics SDK. Wire your own analytics facade to `log`:

```kotlin
val snoop = SnoopAnalytics {
    filter { event -> !event.name.startsWith("debug_") }
    sanitizeProperty { key -> key in setOf("user_email", "user_id") }
    annotate { event -> if (event.name in ecommerceEvents) listOf(Annotation.Ecommerce) else emptyList() }
}

// from your facade, alongside the real SDK call:
snoop.log("purchase", channel = "firebase", properties = mapOf("value" to 42.5))
```

See [Hook your analytics facade](integration-analytics-facade.md) for the pattern.

## Tune retention

The store is an in-memory ring buffer with two drop-oldest limits: entry count and approximate
bytes. Defaults are **500 entries** and **20 MB**. Reconfigure before traffic starts (it clears the
store; the `StateFlow` survives, so an already-open inspector keeps working):

```kotlin
SnoopCore.configure(capacity = 1_000, maxTotalBytes = 40_000_000)
```

How the viewers group the timeline is set on the analytics logger instead — see
[Grouping](filters-redaction-annotations.md#grouping).

Next: [open the inspector](integration.md).
