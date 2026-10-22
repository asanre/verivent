# Web viewer

Verivent runs a tiny embedded HTTP server inside your app and serves the live Verivent timeline as a
web page, so you can inspect traffic from a desktop browser while debugging. It reads the same
in-memory store as the on-device UI, and runs on Android and iOS.

## Add it

Nothing to add: the server (`WebViewer`) ships inside `verivent`, so it already arrives with
`verivent-ui` or `verivent-ktor` — and the no-op mirror keeps `WebViewer` inert in release.

## Start / stop

```kotlin
val url = WebViewer.start()   // WebViewerBind.Local, port 9394, idempotent; "http://localhost:9394/?t=…"
WebViewer.stop()
WebViewer.isRunning
```

`start` returns the URL you'd actually open — the LAN address for a reachable bind, or the local URL
otherwise, always with its access token. Start it from an internal-only entry point (a dev menu, the
sample's "Start web viewer" button, or `Application.onCreate` in the internal variant).

The bind itself is asynchronous: `start` returns before the port is actually listening, so a failure
(port already taken, restricted environment) shows up afterwards as a `Verivent` warning in Logcat or
the device console, with `isRunning` back to false. On success the same log line carries the full
URL, token included — that is where you copy it from.

Where it listens is a `WebViewerBind`:

- `WebViewerBind.Local` (default) — loopback only (`127.0.0.1`).
- `WebViewerBind.Lan` — every interface (`0.0.0.0`), reachable across the LAN.
- `WebViewerBind.Host("192.168.1.5")` — a specific interface.

All three are token-gated; see [Access token](#access-token).

## Open it from your Mac

By default the server binds loopback only. Every URL below needs its `?t=…` token — take the one
`start` returned, or read it from the `Verivent` log line. How you reach it depends on the platform:

- **Android (device or emulator)** — forward the port over adb, then open the URL `start` returned:

  ```bash
  adb forward tcp:9394 tcp:9394
  ```

- **iOS simulator** — open the URL directly, no forwarding. The simulator shares the Mac's network
  stack, so the app's loopback listener is the Mac's loopback.

- **Physical iOS device** — bind to the LAN and browse to the device's Wi-Fi IP:

  ```kotlin
  val url = WebViewer.start(WebViewerBind.Lan)   // "http://192.168.1.10:9394/?t=…" — open this from your Mac
  WebViewer.lanUrl()   // the same reachable URL, recomputed on demand
  WebViewer.token      // the token that URL carries
  ```

  `lanUrl()` is null while bound with `WebViewerBind.Local` or when no LAN address is found.

The page streams updates live over SSE — new network calls and analytics events appear as they
happen, as deltas rather than a full re-send of the store. It mirrors the native inspector: unified
timeline, type and channel filters, search, per-screen
[grouping](filters-redaction-annotations.md#grouping) (which rides along on the entries themselves,
so the page needs no configuration of its own), and a detail panel with headers (redaction preserved), pretty-printed bodies,
and an event's [extra parameter blocks](integration-analytics-facade.md#extra-parameter-blocks) in their own block.

The download button saves the session's analytics events as a JSON file, the same
[export](integration-analytics-facade.md#exported-sessions) the inspector writes. Network calls
stay in the live timeline and aren't exported.

## Tracking plan

The page shows the device's [tracking plan](integration-analytics-facade.md#tracking-plan) verdicts
like the inspector does:

- The header chip reads *Plan x.y · n break it*, or *No plan*. It opens the plan panel, where you
  see the loaded version and its coverage (the events the plan defines that break it, match it or
  haven't been seen yet), clear it, or load another one: drop the JSON file, pick it, or paste it.
  An invalid plan is rejected inline and leaves the loaded one untouched.
- *Breaks plan · n* filters the timeline down to the events that break the loaded plan.
- An event row carries `✕ n` when it breaks the plan and `✓` when it matches. Its detail lists the
  problems, marks the parameters at fault, and adds the required ones that weren't sent.

The plan lives on the device; the page only reads it and sends what you load. It isn't streamed:
the page reads it again when you open the panel, when the tab regains focus, and when an event
arrives checked against another version. A plan cleared on the device stays on the page until one
of those happens.

## Several developers on one network

Each device runs its own server over its own network stack, serving only its own in-memory store.
`WebViewerBind.Lan` listens on every interface **of this device**, and the port is per-host — so three
phones are `…10:9394`, `…11:9394`, `…12:9394` and never collide. Every developer opens their own
device's `lanUrl()` and sees only that device's logs.

## Access token

**Every bind is token-gated, loopback included.** Captured bodies routinely carry session tokens and
personal data, and loopback is not private: on Android every installed app shares `127.0.0.1`, so an
open `9394` would hand the whole capture buffer to any co-installed app. Pass your own token, or let
`start` generate one and read it back:

```kotlin
WebViewer.start()                                       // token generated, in the return value
WebViewer.start(WebViewerBind.Lan, token = "team-standup")  // or pick your own
WebViewer.token                                         // the token the running server expects
```

The token gates every route, and both `start` and `lanUrl()` carry it as `?t=…`, so the link works
as-is. A URL without it gets a `401` — the page then shows "Can't reach Verivent" instead of data.

## Tests over HTTP

The same server tells a black-box test whether any event broke the tracking plan: see
[Testing analytics](testing.md#over-http).

## Notes

- **Android** needs the `INTERNET` permission (to open the listening socket). iOS needs no
  permission to listen — the Local Network privacy prompt applies to outgoing connections, not an
  inbound listener.
- **iOS backgrounding** suspends the app process, which pauses the server; keep the app foregrounded
  while inspecting.
- **Release builds on KMP/iOS**: the `debugImplementation`/`releaseImplementation` swap is
  Android-specific. KMP consumers either swap `verivent-*` for `verivent-*-no-op` at their own build level
  (a Gradle property or flavor) or simply never call `WebViewer.start()` in release — the no-op
  mirror has the identical API, so call sites compile either way.
- The page is fully self-contained (inline CSS/JS, no CDN) and served from the app itself.
- Redacted header/property values arrive already replaced with the `██` placeholder — secrets never
  leave the device.
