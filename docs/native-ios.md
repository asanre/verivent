# Native iOS

Snoop's inspector is a Compose Multiplatform surface, so a host with a shared KMP module gets
everything described in [Integration](integration.md). An app written entirely in Swift has no Ktor
client to hook and no Compose tree to embed — for those, Snoop ships a separate Swift Package with a
deliberately narrower scope: the analytics sink, the web viewer, and network capture through
`URLSession` rather than Ktor.

!!! warning "Network capture here is experimental, and partial by nature"
    `URLProtocol` only sees the sessions you install it on. It cannot see `URLSession.shared`,
    WebSockets, background sessions, or the sessions a third-party SDK builds for itself. And
    because the request is replayed through Snoop's own session, **your `URLSessionDelegate` is not
    consulted for intercepted requests** — see [Capture network traffic](#capture-network-traffic).
    The API is a preview and may change in a minor release.

## Install

Add `https://github.com/asanre/snoop-swift` in Xcode (*File → Add Package Dependency*), or declare
it directly:

```swift
.package(url: "https://github.com/asanre/snoop-swift.git", from: "0.4.0")
```

That repository is generated on every release; the sources live in `swift/` in the main repo,
alongside a runnable example: `sample/iosApp` is a SwiftUI app wired to everything below.

## Capture analytics events

Snoop never talks to an analytics SDK. Wire your own facade to `log`, alongside the real call:

```swift
import Snoop

Snoop.configure(
    keepEvent: { !$0.name.hasPrefix("debug_") },
    redactProperty: { $0 == "user_email" },
    groupByEvent: "screen_view",        // stamped on events as they're captured; nil turns grouping off
    groupLabelProperty: "screen_name"   // the property that labels each group
)

Snoop.log("purchase", channel: "firebase", properties: [
    "sku": "SKU-9",
    "value": 42.5,
    "first": true,
])
```

Grouping parameters left out of `configure` keep their current values; a closure left out is
cleared, so pass both when adjusting one. Grouping is applied as events are captured, so changing it
affects events logged from then on rather than regrouping what is already in the buffer. Both closures are held until the next `configure` call —
in practice, for the life of the process — so capture `[weak self]` if one reaches into a view
controller or any object you expect to be deallocated. Property values cross into Kotlin as strings,
so `String`, `Int`, `Double` and `Bool` all work.
Redaction runs on the same engine as the Compose Multiplatform build — a matching key is replaced
with a visible `██` rather than dropped.

## Capture network traffic

Install the interceptor on the `URLSessionConfiguration` your app builds its session from, and every
call through that session lands in the timeline next to your analytics events:

```swift
let configuration = URLSessionConfiguration.default
Snoop.installNetworkInterceptor(
    on: configuration,
    maxContentLength: 250_000,
    redactHeader: { $0.lowercased() == "x-api-key" },
    redactQueryParameter: { $0 == "session" },
    redactBody: { $0.replacingOccurrences(of: "secret", with: "██") },
    keepRequest: { $0.path != "/health" }
)
let session = URLSession(configuration: configuration, delegate: self, delegateQueue: .main)
```

Redaction runs on the same engine as the Ktor plugin, so the defaults come along: `Authorization`,
`Cookie` and `Set-Cookie` are always redacted, as are the usual secret-bearing query parameters
(`api_key`, `token`, `access_token`, …) in the stored URL, and `password=`-style fields in a
form-urlencoded body. A predicate that throws counts as a match, so a broken rule fails closed.
Unlike the Ktor DSL, whose rules are additive, each call **replaces** the previous configuration —
the interceptor keeps one set of options for the process, so the last install wins.

`Snoop.log` and the interceptor are independent: `redactProperty` in `configure` covers analytics
properties, `redactHeader` here covers HTTP headers.

One cosmetic difference from the Ktor side: the protocol column shows the ALPN name URLSession
negotiated (`http/1.1`, `h2`, `h3`) rather than Ktor's `HTTP/1.1` spelling, because that is the only
form Foundation reports.

### What it cannot see

- **`URLSession.shared`** — its `protocolClasses` is not configurable. Build your own session.
- **WebSockets and background sessions** — outside what `URLProtocol` is handed.
- **Third-party SDKs** that construct their own session, unless they let you pass a configuration.
- **Streamed upload bodies without a `Content-Length`**, or larger than 1 MB: a body stream is
  one-shot, so rather than risk corrupting the upload Snoop stores a placeholder and sends the
  stream through untouched.

### Authentication and certificate pinning

The intercepted request is replayed through Snoop's own session, so **the host's
`URLSessionDelegate` never sees it**. Certificate pinning implemented in
`urlSession(_:didReceive:completionHandler:)` does not run, and a credential you would have supplied
for a Basic/Digest challenge goes unanswered. The connection still gets the system's default trust
evaluation — this is no weaker than not intercepting at all — but it does drop a check you added on
top. If a session relies on one, simply don't install the interceptor on that configuration; that is
what makes per-configuration installation the escape hatch rather than a global switch.

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

`#if DEBUG` keeps the calls out, but not the binary: the arm64 device slice is ~5.7 MB. If that is
unacceptable, keep Snoop out of the release target entirely rather than relying on dead-stripping —
Objective-C class metadata resists it.
