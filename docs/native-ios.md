# Native iOS

Snoop's inspector is a Compose Multiplatform surface, so a host with a shared KMP module gets
everything described in [Integration](integration.md). An app written entirely in Swift has no Ktor
client to hook and no Compose tree to embed — for those, Snoop ships a separate Swift Package with a
deliberately narrower scope.

!!! warning "What this package does *not* do"
    **There is no network capture.** Snoop intercepts a Ktor `HttpClient`; a native app uses
    `URLSession`, which the package never touches. What you get is the analytics timeline and the
    web viewer.

## Install

Add `https://github.com/asanre/snoop-swift` in Xcode (*File → Add Package Dependency*), or declare
it directly:

```swift
.package(url: "https://github.com/asanre/snoop-swift.git", from: "0.2.0")
```

That repository is generated on every release; the sources live in `swift/` in the main repo.

## Capture analytics events

Snoop never talks to an analytics SDK. Wire your own facade to `log`, alongside the real call:

```swift
import Snoop

Snoop.configure(
    keepEvent: { !$0.name.hasPrefix("debug_") },
    redactProperty: { $0 == "user_email" },
    groupByEvent: "screen_view",        // groups the viewer's timeline; nil turns grouping off
    groupLabelProperty: "screen_name"   // the property that labels each group
)

Snoop.log("purchase", channel: "firebase", properties: [
    "sku": "SKU-9",
    "value": 42.5,
    "first": true,
])
```

Grouping parameters left out of `configure` keep their current values; a closure left out is
cleared, so pass both when adjusting one. Both closures are held until the next `configure` call —
in practice, for the life of the process — so capture `[weak self]` if one reaches into a view
controller or any object you expect to be deallocated. Property values cross into Kotlin as strings,
so `String`, `Int`, `Double` and `Bool` all work.
Redaction runs on the same engine as the Compose Multiplatform build — a matching key is replaced
with a visible `██` rather than dropped.

## Inspect

```swift
let url = Snoop.startWebViewer()          // http://localhost:9394/?t=…
Snoop.stopWebViewer()
```

Open that URL from your Mac's browser — the simulator shares the host loopback. It carries the
access token every bind requires, loopback included, so open it as returned rather than typing the
bare host and port (`Snoop.webViewerToken` exposes the token on its own). For a physical device,
bind to the LAN and open the returned URL:

```swift
let url = Snoop.startWebViewer(.lan)      // http://192.168.1.10:9394/?t=…
```

See [Web viewer](web-viewer.md) for bind modes and tokens; the page, filters and detail views are
the same ones the KMP build serves.

!!! note "`startWebViewer` reports bind failures asynchronously"
    The engine binds on a background coroutine, so the returned URL is not proof that the port was
    free. A failure logs a `Snoop` warning to the device console and flips `Snoop.isWebViewerRunning`
    back to false; a success logs the same URL. Check it a moment later, or just try the URL.

## Release builds

This is the one place the Swift Package is weaker than the Gradle artifacts. SPM has no
per-configuration dependencies — no equivalent of `debugImplementation` — so there is no no-op mirror
to swap in and **the framework links into your release binary too**. Guard your call sites:

```swift
#if DEBUG
Snoop.log(event.name, channel: "firebase", properties: event.properties)
#endif
```

`#if DEBUG` keeps the calls out, but not the binary: the arm64 device slice is ~5.6 MB. If that is
unacceptable, keep Snoop out of the release target entirely rather than relying on dead-stripping —
Objective-C class metadata resists it.
