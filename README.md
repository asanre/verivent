# Snoop

On-device inspector for **network calls** and **analytics events** in Compose Multiplatform apps.
One unified timeline, a Ktor plugin, a vendor-agnostic analytics sink, and a Material 3 UI you can
open while you navigate the host app. Inspired by [Chucker](https://github.com/ChuckerTeam/chucker)
and [KtorMonitor](https://github.com/cosminmihu/KtorMonitor).

> Status: v1 in development. Network capture is **Ktor-only**; analytics capture is vendor-agnostic.

## Why

Network inspection in KMP is a solved problem, but nothing unifies **network + analytics** in one
Compose Multiplatform surface, and no tool inspects analytics events independently of the vendor
SDK. Snoop does both, and correlates analytics events to the request that carried them.

## Modules

You add one artifact per capture source; everything else arrives transitively.

| Artifact | What it does | Brings along |
|---|---|---|
| `snoop-ktor` | Ktor client plugin that captures traffic | inspector UI, web viewer |
| `snoop-analytics` | Vendor-agnostic `log(name, channel, properties)` sink | inspector UI, web viewer |
| `snoop-*-no-op` | Empty mirrors with the identical API for release builds | matching no-op stubs |

Internally those share `snoop-core` (models, ring buffer store, redaction, Compose UI) and
`snoop-web` (embedded HTTP server) — you never declare them yourself.

## Install

Use the real artifacts in debug and the no-op mirrors in release so release builds capture nothing
and drop the UI/body-retention overhead:

```kotlin
debugImplementation("dev.snoop:snoop-ktor:<version>")
releaseImplementation("dev.snoop:snoop-ktor-no-op:<version>")

// optional, only if you also want analytics inspection:
debugImplementation("dev.snoop:snoop-analytics:<version>")
releaseImplementation("dev.snoop:snoop-analytics-no-op:<version>")
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

// Or, on Android, as its own screen / a persistent notification:
Snoop.open(context)
Snoop.showNotification(context)   // ongoing notification that opens the inspector; needs POST_NOTIFICATIONS on API 33+
```

## How capture works

The Ktor plugin assigns a call id on `SendingRequest`, records the request (method, url, headers,
body) into the shared store, then fills in status/headers/body/duration on `onResponse`, and records
transport exceptions via an `HttpSend` interceptor. Every capture path is wrapped in try/catch, so
the debug tooling can never break a real request. Response bodies are read through Ktor 3's saved
body, so the host still consumes them normally. Sensitive header/property values are replaced with a
visible `██` placeholder — you see that a field exists, not its value.

## Correlation

Mark the requests that carry analytics batches and Snoop links recent events to them:

```kotlin
install(Snoop) {
    markAsAnalyticsTransport { it.url.host == "collector.example.com" }
}
```

The event detail then shows "sent over POST /collect → 204" with a tap-through to the request.

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
  SnoopWebServer.start(host = "0.0.0.0")   // then read SnoopWebServer.lanUrl, e.g. http://192.168.1.10:9394
  ```

  Each device serves only its own logs, so three developers on three phones don't collide — each
  opens their own device's IP. On an untrusted network add `token = "…"` to gate access.

Live timeline over SSE, same filters and detail as the native UI. See [docs/web-viewer.md](docs/web-viewer.md).

## Sample

`sample/composeApp` is a Compose Multiplatform app (Android + iOS) that generates real Ktor traffic
and fake analytics events across channels, and opens the inspector embedded and via notification.

```bash
./gradlew :sample:composeApp:assembleDebug            # Android APK
./gradlew :sample:composeApp:linkDebugFrameworkIosSimulatorArm64   # iOS framework
```

## Publishing

Not yet wired. The intended setup is `com.vanniktech.maven.publish` to Maven Central under
`dev.snoop`. See the roadmap in the plan.

## License

Apache-2.0. See [LICENSE](LICENSE).
