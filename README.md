# Snoop

[![Maven Central](https://img.shields.io/maven-central/v/io.github.asanre/snoop-core)](https://central.sonatype.com/namespace/io.github.asanre)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS-informational)

On-device inspector for **network calls** and **analytics events** in Compose Multiplatform apps.
One unified timeline, a Ktor plugin, a vendor-agnostic analytics sink, and a Material 3 UI you can
open while you navigate the host app.

📖 **Full documentation: <https://asanre.github.io/snoop/>**

> Network capture is **Ktor-only**; analytics capture is vendor-agnostic.

## Why

Network inspection in KMP is a solved problem, but nothing unifies **network + analytics** in one
Compose Multiplatform surface, and no tool inspects analytics events independently of the vendor
SDK. Snoop does both, on one timeline.

## Modules

You add one artifact per capture source; `snoop-core` (models, ring-buffer store, redaction, Compose
UI and the embedded web viewer) arrives transitively — you never declare it yourself.

| Artifact | What it does |
|---|---|
| `snoop-core` | Models, store, redaction, Compose UI and web viewer (transitive) |
| `snoop-ktor` | Ktor client plugin that captures traffic |
| `snoop-analytics` | Vendor-agnostic `log(name, channel, properties)` sink |
| `snoop-core-no-op` | Release mirror of core — identical API, captures nothing |
| `snoop-ktor-no-op` | Release mirror of the Ktor plugin |
| `snoop-analytics-no-op` | Release mirror of the analytics sink |

## Install

Use the real artifacts in debug and the no-op mirrors in release so release builds capture nothing
and drop the UI/body-retention overhead:

```kotlin
debugImplementation("io.github.asanre:snoop-ktor:0.1.0")
releaseImplementation("io.github.asanre:snoop-ktor-no-op:0.1.0")

// optional, only if you also want analytics inspection:
debugImplementation("io.github.asanre:snoop-analytics:0.1.0")
releaseImplementation("io.github.asanre:snoop-analytics-no-op:0.1.0")
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
SnoopWebServer.start()   // http://localhost:9394
```

By default it binds loopback only. Reach it from your Mac by platform:

```bash
adb forward tcp:9394 tcp:9394   # Android: then open http://localhost:9394
```

- **iOS simulator** — open <http://localhost:9394> directly (the simulator shares the Mac's loopback).
- **Physical device** — bind to the LAN and open the device's IP from your Mac (same Wi-Fi):

  ```kotlin
  SnoopWebServer.start(SnoopBind.Lan)   // returns the reachable URL, e.g. http://192.168.1.10:9394/?t=…
  ```

  Each device serves only its own logs, so three developers on three phones don't collide — each
  opens their own device's IP. `SnoopBind.Lan` (and `SnoopBind.Host`) are always token-gated: `start`
  generates one unless you pass `token = "…"`, and both `start`'s return value and `lanUrl()` carry it.
  `SnoopBind.Local` (the default) is loopback-only and open.

Live timeline over SSE, same filters and detail as the native UI. See [docs/web-viewer.md](docs/web-viewer.md).

## Sample

`sample/composeApp` is a Compose Multiplatform app (Android + iOS) that generates real Ktor traffic
and fake analytics events across channels, and opens the inspector embedded and via notification.

```bash
./gradlew :sample:composeApp:assembleDebug            # Android APK
./gradlew :sample:composeApp:linkDebugFrameworkIosSimulatorArm64   # iOS framework
```

## Publishing

Released to Maven Central under `io.github.asanre` via `com.vanniktech.maven.publish`; the `publish`
workflow runs on each GitHub Release. See [docs/releasing.md](docs/releasing.md).

## License

Apache-2.0. See [LICENSE](LICENSE).

---

Some ideas in this project come from [Chucker](https://github.com/ChuckerTeam/chucker) and
[KtorMonitor](https://github.com/CosminMihuMDC/KtorMonitor). Thanks to both teams.
