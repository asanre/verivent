# Web viewer

`snoop-web` runs a tiny embedded HTTP server inside your app and serves the live Snoop timeline as a
web page, so you can inspect traffic from a desktop browser while debugging. It reads the same
in-memory store as the on-device UI, and runs on Android and iOS.

## Add it

```kotlin
debugImplementation("dev.snoop:snoop-web:<version>")
releaseImplementation("dev.snoop:snoop-web-no-op:<version>")
```

## Start / stop

```kotlin
val url = SnoopWebServer.start()   // default port 9394, idempotent; returns "http://localhost:9394"
SnoopWebServer.stop()
SnoopWebServer.isRunning
```

Start it from an internal-only entry point (a dev menu, the sample's "Start web viewer" button, or
`Application.onCreate` in the internal variant).

## Open it from your Mac

By default the server binds loopback only (`127.0.0.1`). How you reach it depends on the platform:

- **Android (device or emulator)** — forward the port over adb, then open <http://localhost:9394>:

  ```bash
  adb forward tcp:9394 tcp:9394
  ```

- **iOS simulator** — open <http://localhost:9394> directly, no forwarding. The simulator shares the
  Mac's network stack, so the app's loopback listener is the Mac's loopback.

- **Physical iOS device** — bind to the LAN and browse to the device's Wi-Fi IP:

  ```kotlin
  val url = SnoopWebServer.start(host = "0.0.0.0")
  SnoopWebServer.lanUrl()   // "http://192.168.1.10:9394/?t=…" — open this from your Mac
  SnoopWebServer.token      // the token that URL carries
  ```

  `lanUrl()` is null while bound to loopback or when no LAN address is found.

The page streams updates live over SSE — new network calls and analytics events appear as they
happen. It mirrors the native inspector: unified timeline, type and channel filters, search, and a
detail panel with headers (redaction preserved), pretty-printed bodies, and event↔request correlation.

## Several developers on one network

Each device runs its own server over its own network stack, serving only its own in-memory store.
`0.0.0.0:9394` means "listen on every interface **of this device**", and the port is per-host — so
three phones are `…10:9394`, `…11:9394`, `…12:9394` and never collide. Every developer opens their
own device's `lanUrl()` and sees only that device's logs.

LAN binds are always token-gated: captured bodies routinely carry session tokens and personal data,
so a `0.0.0.0` bind never serves them to the whole network unauthenticated. Pass your own token, or
let `start` generate one and read it back:

```kotlin
SnoopWebServer.start(host = "0.0.0.0")                          // token generated, see lanUrl()
SnoopWebServer.start(host = "0.0.0.0", token = "team-standup")  // or pick your own
```

The token gates every route, and `lanUrl()` carries it as `?t=…` so the link works as-is. Loopback
binds stay open — nothing off-device can reach them.

## Notes

- **Android** needs the `INTERNET` permission (to open the listening socket). iOS needs no
  permission to listen — the Local Network privacy prompt applies to outgoing connections, not an
  inbound listener.
- **iOS backgrounding** suspends the app process, which pauses the server; keep the app foregrounded
  while inspecting.
- **Release builds on KMP/iOS**: the `debugImplementation`/`releaseImplementation` swap is
  Android-specific. KMP consumers either swap `snoop-*` for `snoop-*-no-op` at their own build level
  (a Gradle property or flavor) or simply never call `SnoopWebServer.start()` in release — the no-op
  mirror has the identical API, so call sites compile either way.
- The page is fully self-contained (inline CSS/JS, no CDN) and served from the app itself.
- Redacted header/property values arrive already replaced with the `██` placeholder — secrets never
  leave the device.
