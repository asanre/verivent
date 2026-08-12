# Web viewer

Snoop runs a tiny embedded HTTP server inside your app and serves the live Snoop timeline as a
web page, so you can inspect traffic from a desktop browser while debugging. It reads the same
in-memory store as the on-device UI, and runs on Android and iOS.

## Add it

Nothing to add: the server (`SnoopWebServer`) ships inside `snoop-core`, so it already arrives with
`snoop-ktor` or `snoop-analytics` — and the no-op mirror keeps `SnoopWebServer` inert in release.

## Start / stop

```kotlin
val url = SnoopWebServer.start()   // SnoopBind.Local, port 9394, idempotent; "http://localhost:9394/?t=…"
SnoopWebServer.stop()
SnoopWebServer.isRunning
```

`start` returns the URL you'd actually open — the LAN address for a reachable bind, or the local URL
otherwise, always with its access token. Start it from an internal-only entry point (a dev menu, the
sample's "Start web viewer" button, or `Application.onCreate` in the internal variant).

The bind itself is asynchronous: `start` returns before the port is actually listening, so a failure
(port already taken, restricted environment) shows up afterwards as a `Snoop` warning in Logcat or
the device console, with `isRunning` back to false. On success the same log line carries the full
URL, token included — that is where you copy it from.

Where it listens is a `SnoopBind`:

- `SnoopBind.Local` (default) — loopback only (`127.0.0.1`).
- `SnoopBind.Lan` — every interface (`0.0.0.0`), reachable across the LAN.
- `SnoopBind.Host("192.168.1.5")` — a specific interface.

All three are token-gated; see [Access token](#access-token).

## Open it from your Mac

By default the server binds loopback only. Every URL below needs its `?t=…` token — take the one
`start` returned, or read it from the `Snoop` log line. How you reach it depends on the platform:

- **Android (device or emulator)** — forward the port over adb, then open the URL `start` returned:

  ```bash
  adb forward tcp:9394 tcp:9394
  ```

- **iOS simulator** — open the URL directly, no forwarding. The simulator shares the Mac's network
  stack, so the app's loopback listener is the Mac's loopback.

- **Physical iOS device** — bind to the LAN and browse to the device's Wi-Fi IP:

  ```kotlin
  val url = SnoopWebServer.start(SnoopBind.Lan)   // "http://192.168.1.10:9394/?t=…" — open this from your Mac
  SnoopWebServer.lanUrl()   // the same reachable URL, recomputed on demand
  SnoopWebServer.token      // the token that URL carries
  ```

  `lanUrl()` is null while bound with `SnoopBind.Local` or when no LAN address is found.

The page streams updates live over SSE — new network calls and analytics events appear as they
happen, as deltas rather than a full re-send of the store. It mirrors the native inspector: unified
timeline, type and channel filters, search, per-screen grouping (whatever
[`SnoopCore.configure`](integration.md#group-the-timeline-by-screen) set, which the page reads from
`/api/config`), and a detail panel with headers (redaction preserved) and pretty-printed bodies.

## Several developers on one network

Each device runs its own server over its own network stack, serving only its own in-memory store.
`SnoopBind.Lan` listens on every interface **of this device**, and the port is per-host — so three
phones are `…10:9394`, `…11:9394`, `…12:9394` and never collide. Every developer opens their own
device's `lanUrl()` and sees only that device's logs.

## Access token

**Every bind is token-gated, loopback included.** Captured bodies routinely carry session tokens and
personal data, and loopback is not private: on Android every installed app shares `127.0.0.1`, so an
open `9394` would hand the whole capture buffer to any co-installed app. Pass your own token, or let
`start` generate one and read it back:

```kotlin
SnoopWebServer.start()                                       // token generated, in the return value
SnoopWebServer.start(SnoopBind.Lan, token = "team-standup")  // or pick your own
SnoopWebServer.token                                         // the token the running server expects
```

The token gates every route, and both `start` and `lanUrl()` carry it as `?t=…`, so the link works
as-is. A URL without it gets a `401` — the page then shows "Can't reach Snoop" instead of data.

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
