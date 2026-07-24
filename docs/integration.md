# Integration

Once you're capturing traffic and events, open the inspector.

## Embed the inspector (common)

`SnoopScreen` is a Compose Multiplatform composable — host it anywhere in your debug UI (a dev menu,
a dedicated tab, behind a gesture):

```kotlin
SnoopScreen()

// or, grouped by a screen-tracking event and sized by a modifier:
SnoopScreen(modifier = Modifier.fillMaxSize(), groupByEvent = "screen_view")
```

`groupByEvent` collapses the timeline into per-screen sections keyed by that analytics event.

## Android

`Snoop` exposes two extra entry points that take a `Context`:

```kotlin
Snoop.open(context)                 // launch the inspector as its own Activity
Snoop.showNotification(context)     // post a live, ongoing notification
Snoop.dismissNotification(context)  // remove it
```

The notification lists the latest captures and refreshes as traffic arrives; tapping it opens the
inspector, and it carries a **Clear** action. It is ongoing until dismissed.

!!! note "POST_NOTIFICATIONS"
    On Android 13+ the host app owns the `POST_NOTIFICATIONS` runtime permission. Snoop never
    requests it; if it isn't granted, `showNotification` is a silent no-op (the `SecurityException`
    is swallowed).

## iOS

`Snoop` mirrors the Android object without a `Context`:

```kotlin
Snoop.showNotification()     // request alert permission inline, then keep it in sync
Snoop.dismissNotification()  // cancel and remove pending + delivered
```

`showNotification()` asks for alert permission the first time via `UNUserNotificationCenter`
(idempotent; a denial is a silent no-op). It installs a delegate that presents Snoop's own
notification as a foreground banner while forwarding everything else to whatever delegate your app
already set. Each capture republishes a single notification (fixed identifier), so it updates in
place instead of stacking.

The inspector itself is the shared `SnoopScreen` — embed it in your SwiftUI/Compose host as above.

## Web viewer

For inspecting from a desktop browser, start the embedded server:

```kotlin
val url = SnoopWebServer.start()   // loopback, port 9394; returns the URL to open
SnoopWebServer.stop()
```

It streams the same timeline over SSE and runs on Android and iOS. See
[Web viewer](web-viewer.md) for bind modes, LAN access and tokens.
