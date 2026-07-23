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

The server binds loopback only (`127.0.0.1`), so it is never reachable from other devices on the
LAN. How you reach it depends on the platform:

- **Android (device or emulator)** — forward the port over adb, then open <http://localhost:9394>:

  ```bash
  adb forward tcp:9394 tcp:9394
  ```

- **iOS simulator** — open <http://localhost:9394> directly, no forwarding. The simulator shares the
  Mac's network stack, so the app's loopback listener is the Mac's loopback.

- **Physical iOS device** — not reachable from a desktop browser: there is no adb-style tunnel to the
  device loopback, and the server does not listen on the Wi-Fi interface. Use the embedded
  `SnoopScreen` on-device instead.

The page streams updates live over SSE — new network calls and analytics events appear as they
happen. It mirrors the native inspector: unified timeline, type and channel filters, search, and a
detail panel with headers (redaction preserved), pretty-printed bodies, and event↔request correlation.

## Notes

- **Android** needs the `INTERNET` permission (to open the listening socket). iOS needs no
  permission to listen — the Local Network privacy prompt applies to outgoing connections, not an
  inbound loopback listener.
- **iOS backgrounding** suspends the app process, which pauses the server; keep the app foregrounded
  while inspecting.
- **Release builds on KMP/iOS**: the `debugImplementation`/`releaseImplementation` swap is
  Android-specific. KMP consumers either swap `snoop-*` for `snoop-*-no-op` at their own build level
  (a Gradle property or flavor) or simply never call `SnoopWebServer.start()` in release — the no-op
  mirror has the identical API, so call sites compile either way.
- The page is fully self-contained (inline CSS/JS, no CDN) and served from the app itself.
- Redacted header/property values arrive already replaced with the `██` placeholder — secrets never
  leave the device.
