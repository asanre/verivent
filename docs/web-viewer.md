# Web viewer

`snoop-web` runs a tiny embedded HTTP server inside your app and serves the live Snoop timeline as a
web page, so you can inspect traffic from a desktop browser while debugging from Android Studio. It
reads the same in-memory store as the on-device UI. **Android-only in v1.**

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

The server listens on the device/emulator. Forward the port over adb and open it in your browser:

```bash
adb forward tcp:9394 tcp:9394
```

Then open <http://localhost:9394>. The page streams updates live over SSE — new network calls and
analytics events appear as they happen. It mirrors the native inspector: unified timeline, type and
channel filters, search, and a detail panel with headers (redaction preserved), pretty-printed
bodies, and event↔request correlation.

On a physical device on the same Wi-Fi you can also browse to `http://<device-ip>:9394` directly.

## Notes

- Needs the `INTERNET` permission (to open the listening socket).
- The page is fully self-contained (inline CSS/JS, no CDN) and served from the app itself.
- Redacted header/property values arrive already replaced with the `██` placeholder — secrets never
  leave the device.
