# Snoop

![Snoop — network and analytics inspector for Compose Multiplatform](docs/assets/banner.webp)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.asanre/snoop)](https://central.sonatype.com/namespace/io.github.asanre)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS-informational)

On-device inspector for **network calls** and **analytics events** in Compose Multiplatform apps.
One unified timeline, a Ktor plugin, a vendor-agnostic analytics sink, and a Material 3 UI you can
open while you navigate the host app.

📖 **Full documentation: <https://asanre.github.io/snoop/>**

> Network capture is **Ktor**, plus experimental `URLSession` capture on native iOS; analytics
> capture is vendor-agnostic.

## Why

Network inspection in KMP is a solved problem, but nothing unifies **network + analytics** in one
Compose Multiplatform surface, and no tool inspects analytics events independently of the vendor
SDK. Snoop does both, on one timeline.

## Modules

`snoop` carries everything that has no UI and no HTTP client: models, ring-buffer store, redaction,
the vendor-agnostic analytics sink and the embedded web viewer. Add `snoop-ui` for the on-device
inspector and one adapter per HTTP client.

| Artifact | What it does |
|---|---|
| `snoop` | Models, store, redaction, web viewer and the `log(name, channel, properties, sections)` analytics sink |
| `snoop-ui` | Compose Multiplatform inspector UI and live notifications (brings `snoop`) |
| `snoop-ktor` | Ktor client plugin that captures traffic (brings `snoop`) |
| `snoop-no-op` | Release mirror of `snoop` — identical API, captures nothing |
| `snoop-ui-no-op` | Release mirror of the inspector UI — renders nothing |
| `snoop-ktor-no-op` | Release mirror of the Ktor plugin |

`snoop-ui` is the only artifact that depends on Compose. Leave it out if you inspect from the web
viewer instead of on-device, and Compose never enters your dependency graph.

Apps written entirely in Swift, with no shared KMP module, install a separate Swift Package that
carries the analytics sink, the web viewer and experimental `URLSession` capture. See
[Native iOS](https://asanre.github.io/snoop/native-ios/).

## Install

Use the real artifacts in debug and the no-op mirrors in release so release builds capture nothing
and drop the UI/body-retention overhead:

```kotlin
debugImplementation("io.github.asanre:snoop:0.7.0")
releaseImplementation("io.github.asanre:snoop-no-op:0.7.0")

// the on-device inspector UI:
debugImplementation("io.github.asanre:snoop-ui:0.7.0")
releaseImplementation("io.github.asanre:snoop-ui-no-op:0.7.0")

// network capture for Ktor clients:
debugImplementation("io.github.asanre:snoop-ktor:0.7.0")
releaseImplementation("io.github.asanre:snoop-ktor-no-op:0.7.0")
```

The no-op mirrors declare the exact same classes and functions, so your call sites compile unchanged.

## Quick start

### 1. Capture network traffic

```kotlin
val client = HttpClient(engine) {
    install(Snoop) {
        maxContentLength = 250_000
        sanitizeHeader { it == HttpHeaders.Authorization }   // Authorization/Cookie/Set-Cookie are redacted by default
        filter { request -> request.url.host != "metrics.internal" }
    }
}
```

### 2. Capture analytics events

Snoop never talks to an analytics SDK. Wire your own analytics facade to `log`:

```kotlin
val snoop = SnoopAnalytics {
    filter { event -> !event.name.startsWith("debug_") }
    sanitizeProperty { key -> key in setOf("user_email", "user_id") }
    annotate { event -> if (event.name in ecommerceEvents) listOf(Annotation.Ecommerce) else emptyList() }
}

// from your facade:
snoop.log(name = event.name, channel = "firebase", properties = event.properties)
```

See [docs/integration-analytics-facade.md](docs/integration-analytics-facade.md) for the facade recipe.

### 3. Show the inspector

```kotlin
// Anywhere in your Compose tree:
SnoopScreen()

// Android — as its own screen, or a live notification that lists the latest captures:
Snoop.open(context)
Snoop.showNotification(context)   // ongoing; needs POST_NOTIFICATIONS on API 33+ (the host requests it)

// iOS — a live notification (asks for alert permission inline, no Context):
Snoop.showNotification()
```

The timeline collapses into per-screen groups opened by an analytics event — Firebase's `screen_view`
with its `screen_name` parameter by default. Set on the logger, where events are captured, so every
viewer (inspector, Activity, web page, native iOS) groups the same way:

```kotlin
val snoop = SnoopAnalytics { groupBy("page_shown", "page") }
```

See [Integration](https://asanre.github.io/snoop/integration/) for the full inspector API.

## How capture works

The Ktor plugin assigns a call id on `SendingRequest`, records the request (method, url, headers,
body) into the shared store, then fills in status/headers/body/duration on `onResponse`, and records
transport exceptions via an `HttpSend` interceptor. Every capture path is wrapped in try/catch, so
the debug tooling can never break a real request. Response bodies are read through Ktor 3's saved
body, so the host still consumes them normally. Sensitive header/property values are replaced with a
visible `██` placeholder — you see that a field exists, not its value.

## Web viewer

Inspect from a desktop browser while debugging:

```kotlin
SnoopWebServer.start()   // http://localhost:9394/?t=…
```

Open the URL it returns (also logged under the `Snoop` tag). By default it binds loopback only.
Reach it from your Mac by platform:

```bash
adb forward tcp:9394 tcp:9394   # Android: then open the URL start() returned
```

- **iOS simulator** — open that URL directly (the simulator shares the Mac's loopback).
- **Physical device** — bind to the LAN and open the device's IP from your Mac (same Wi-Fi):

  ```kotlin
  SnoopWebServer.start(SnoopBind.Lan)   // returns the reachable URL, e.g. http://192.168.1.10:9394/?t=…
  ```

  Each device serves only its own logs, so three developers on three phones don't collide — each
  opens their own device's IP. Every bind is token-gated, `SnoopBind.Local` included: loopback is
  shared with every other app on the device. `start` generates a token unless you pass `token = "…"`,
  and both its return value and `lanUrl()` carry it as `?t=…`.

Live timeline over SSE, same filters and detail as the native UI. See [docs/web-viewer.md](docs/web-viewer.md).

## Samples

`sample/composeApp` is a Compose Multiplatform app (Android + iOS) that generates real Ktor traffic
and fake analytics events across channels, and opens the inspector embedded and via notification.

```bash
./gradlew :sample:composeApp:assembleDebug            # Android APK
./gradlew :sample:composeApp:linkDebugFrameworkIosSimulatorArm64   # iOS framework
```

On iOS, open `sample/composeApp/iosApp/ComposeSample.xcodeproj` and run it: a build phase compiles
the Kotlin framework through Gradle.

`sample/iosApp` is a SwiftUI app for the native-iOS Swift Package — analytics, `URLSession`
capture and the web viewer. It links `swift/` by local path, so it exercises the sources in this repo
rather than the last release. Assemble the XCFramework first, then open
`sample/iosApp/SnoopSample.xcodeproj`; see [docs/contributing.md](docs/contributing.md).

## Publishing

Released to Maven Central under `io.github.asanre` via `com.vanniktech.maven.publish`; the `publish`
workflow runs on each GitHub Release. See [docs/releasing.md](docs/releasing.md).

## License

Apache-2.0. See [LICENSE](LICENSE).

---

Some ideas in this project come from [Chucker](https://github.com/ChuckerTeam/chucker) and
[KtorMonitor](https://github.com/CosminMihuMDC/KtorMonitor). Thanks to both teams.
