# Integration

Once you're capturing traffic and events, open the inspector.

!!! note "Everything on this page needs `snoop-ui`"
    `SnoopScreen` and the `Snoop` entry points live in `snoop-ui`, the only artifact that pulls in
    Compose — declare it as shown in [Getting started](getting_started.md). The
    [web viewer](web-viewer.md) needs none of it.

## Embed the inspector (common)

`SnoopScreen` is a Compose Multiplatform composable — host it anywhere in your debug UI (a dev menu,
a dedicated tab, behind a gesture):

```kotlin
SnoopScreen()

// or sized by a modifier:
SnoopScreen(modifier = Modifier.fillMaxSize())
```

`SnoopScreen` insets its own content against the status bar, the navigation bar, display cutouts and
the keyboard, while painting its background edge to edge behind them — so it looks right whether or
not the host app is edge-to-edge.

## Group the timeline by screen

The timeline collapses into per-screen sections opened by an analytics event — `screen_view`
labelled with `screen_name` out of the box. It is set where events are captured, on the analytics
logger, and every viewer groups by what capture stamped:

```kotlin
val snoop = SnoopAnalytics {
    groupBy("page_shown", "page")
    // groupBy(null)  // no grouping at all
}
```

See [Grouping](filters-redaction-annotations.md#grouping) for the details — including that it
applies to events captured from then on, and that the **Group** chip is hidden in a build that
captures no analytics.

## Android

`Snoop` exposes two extra entry points that take a `Context`:

```kotlin
Snoop.open(context)                 // launch the inspector as its own Activity
Snoop.showNotification(context)     // post a live, ongoing notification
Snoop.dismissNotification(context)  // remove it
```

### Where the Activity lands, and its label

`Snoop.open(context)` and the notification both stack `SnoopActivity` **on top of your app's task**,
not in one of its own: `launchMode="singleTask"` picks a task by `taskAffinity`, which defaults to
the host's package. So Back returns to the screen you came from, and the multitasking view keeps
showing a single card — your app's — labelled by *your* launcher activity.

That means `android:label="Snoop"` on `SnoopActivity` is normally invisible. It only surfaces when
Snoop starts a task of its own, which happens if you tap the ongoing notification after the host
task is gone.

If you would rather have the inspector as a **separate card** — handy for putting your app and the
timeline side by side in split screen — give it its own affinity from
**`src/debug/AndroidManifest.xml`**. Debug, because `SnoopActivity` only exists there; the release
no-op ships no manifest and no such class:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <application>
        <activity
            android:name="com.snoop.ui.SnoopActivity"
            android:taskAffinity="${applicationId}.snoop"
            android:label="Patata Inspector"
            tools:replace="android:label" />
    </application>
</manifest>
```

`tools:replace` is what settles the conflict on `android:label`: without it the merger sees two
different values for the same attribute and fails the build. `taskAffinity` needs no `tools:replace`
because the library never sets it. The rest — `exported`, `launchMode`, `theme` — is left out on
purpose; it merges in from the library.

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
