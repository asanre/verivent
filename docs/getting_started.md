# Getting started

This page covers Gradle hosts — Android and Kotlin Multiplatform. An app written entirely in Swift
installs the Swift Package instead: see [Native iOS](native-ios.md).

## Install

Use the real artifacts in debug and the no-op mirrors in release, so release builds capture nothing
and drop the UI/body-retention overhead:

```kotlin
dependencies {
    debugImplementation("io.github.asanre:snoop:0.7.0")
    releaseImplementation("io.github.asanre:snoop-no-op:0.7.0")

    // the on-device inspector UI:
    debugImplementation("io.github.asanre:snoop-ui:0.7.0")
    releaseImplementation("io.github.asanre:snoop-ui-no-op:0.7.0")

    // network capture for Ktor clients:
    debugImplementation("io.github.asanre:snoop-ktor:0.7.0")
    releaseImplementation("io.github.asanre:snoop-ktor-no-op:0.7.0")
}
```

`snoop` holds the models, store, redaction, the analytics sink and the web viewer; `snoop-ui` and
`snoop-ktor` bring it along, so declare it yourself only when you call its API directly. The no-op
mirrors declare the exact same classes and functions, so your call sites compile unchanged.

!!! note "Upgrading from 0.6"
    `log(..., userProperties = …)` became `log(..., sections = mapOf("User properties" to …))`, and
    `AnalyticsEntry.userProperties` became `AnalyticsEntry.sections`. See
    [extra parameter blocks](integration-analytics-facade.md#extra-parameter-blocks).

!!! note "Upgrading from 0.5"
    `snoop-core` and `snoop-analytics` merged into `snoop` (and their no-ops into `snoop-no-op`).
    Replace those coordinates with `snoop` / `snoop-no-op`; packages and APIs are unchanged, so no
    source edits are needed.

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

The same call renames the tool. `title` reaches every viewer — the inspector's header, the Android
and iOS notifications, and the web page's tab and header — so a host that would rather ship its own
name sets it once:

```kotlin
SnoopCore.configure(title = "Patata Inspector")
```

Unlike the limits, changing the title leaves the store alone. That covers every surface Snoop draws;
the one name it cannot set is Android's task label, which is normally your app's anyway — see
[Where the Activity lands](integration.md#where-the-activity-lands-and-its-label).

How the viewers group the timeline is set on the analytics logger instead — see
[Grouping](filters-redaction-annotations.md#grouping).

Next: [open the inspector](integration.md).
