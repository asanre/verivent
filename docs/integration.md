# Integration

Once you're capturing traffic and events, open the inspector.

!!! note "Everything on this page needs `verivent-ui`"
    `InspectorScreen` and the `Verivent` entry points live in `verivent-ui`, the only artifact that pulls in
    Compose — declare it as shown in [Getting started](getting_started.md). The
    [web viewer](web-viewer.md) needs none of it.

## Embed the inspector (common)

`InspectorScreen` is a Compose Multiplatform composable — host it anywhere in your debug UI (a dev menu,
a dedicated tab, behind a gesture):

```kotlin
InspectorScreen()

// or sized by a modifier:
InspectorScreen(modifier = Modifier.fillMaxSize())

// with a back button in the top bar that leaves the inspector:
InspectorScreen(onClose = { navController.popBackStack() })
```

`InspectorLauncher.open()` and the notification already pass `onClose`, so their inspector closes itself.

`InspectorScreen` insets its own content against the status bar, the navigation bar, display cutouts and
the keyboard, while painting its background edge to edge behind them — so it looks right whether or
not the host app is edge-to-edge.

## Group the timeline by screen

The timeline collapses into per-screen sections opened by an analytics event — `screen_view`
labelled with `screen_name` out of the box. It is set where events are captured, on the analytics
logger, and every viewer groups by what capture stamped:

```kotlin
val sink = AnalyticsSink {
    groupBy("page_shown", "page")
    // groupBy(null)  // no grouping at all
}
```

See [Grouping](filters-redaction-annotations.md#grouping) for the details — including that it
applies to events captured from then on, and that a build that captures no analytics shows one flat
list.

## Android

`Verivent` exposes two extra entry points that take a `Context`:

```kotlin
InspectorLauncher.open(context)                 // launch the inspector as its own Activity
InspectorLauncher.showNotification(context)     // post a live, ongoing notification
InspectorLauncher.dismissNotification(context)  // remove it
```

### Where the Activity lands, and its label

`InspectorLauncher.open(context)` and the notification both stack `InspectorActivity` **on top of your app's task**,
not in one of its own: `launchMode="singleTask"` picks a task by `taskAffinity`, which defaults to
the host's package. So Back returns to the screen you came from, and the multitasking view keeps
showing a single card — your app's — labelled by *your* launcher activity.

That means `android:label="Verivent"` on `InspectorActivity` is normally invisible. It only surfaces when
Verivent starts a task of its own, which happens if you tap the ongoing notification after the host
task is gone.

If you would rather have the inspector as a **separate card** — handy for putting your app and the
timeline side by side in split screen — give it its own affinity from
**`src/debug/AndroidManifest.xml`**. Debug, because `InspectorActivity` only exists there; the release
no-op ships no manifest and no such class:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <application>
        <activity
            android:name="io.github.asanre.verivent.ui.InspectorActivity"
            android:taskAffinity="${applicationId}.verivent"
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

This section is for iOS hosts built on a shared KMP module. An app written entirely in Swift installs
the Swift Package instead — see [Native iOS](native-ios.md).

`Verivent` mirrors the Android object without a `Context`:

```kotlin
InspectorLauncher.open()                 // present the inspector as a page sheet over the foreground window
InspectorLauncher.showNotification()     // request alert permission inline, then keep it in sync
InspectorLauncher.dismissNotification()  // cancel and remove pending + delivered
```

`showNotification()` asks for alert permission the first time via `UNUserNotificationCenter`
(idempotent; a denial is a silent no-op). It installs a delegate that presents Verivent's own
notification as a foreground banner while forwarding everything else to whatever delegate your app
already set. Each capture republishes a single notification (fixed identifier), so it updates in
place instead of stacking.

Tapping the notification calls `InspectorLauncher.open()`, which presents the inspector as a page sheet over
whatever is on top — swipe it down to close. Calling it while the inspector is already on top does
nothing. Unlike Android there is no **Clear** action, and a tap on a notification left over from a
previous launch opens nothing: the store lives in memory, so that session's captures are gone, and
the delegate is only installed once `showNotification()` runs again.

### Embed the inspector in your own screen

`InspectorLauncher.open()` covers the common case. To place the inspector yourself — a tab, a dev menu — a
Compose Multiplatform UI embeds `InspectorScreen` as above, and a SwiftUI or UIKit host wraps it in a
`UIViewController` from `iosMain`:

```kotlin
// iosMain, e.g. InspectorViewController.kt
fun InspectorViewController(): UIViewController = ComposeUIViewController { InspectorScreen() }
```

and presents it from Swift. Kotlin exposes top-level functions on a class named after the file
(`InspectorViewController.kt` → `InspectorViewControllerKt`):

```swift
// UIKit
present(InspectorViewControllerKt.InspectorViewController(), animated: true)

// SwiftUI
struct InspectorView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        InspectorViewControllerKt.InspectorViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

InspectorView().ignoresSafeArea()
```

In SwiftUI, let it ignore the safe area: `InspectorScreen` applies the insets to its own content, and
inside the safe area its background stops short of the status bar and the home indicator.

If this is the first Compose UI in the app, add `CADisableMinimumFrameDurationOnPhone` = `true` to
its `Info.plist`: Compose Multiplatform crashes at runtime without it (or set
`ComposeUIViewControllerConfiguration.enforceStrictPlistSanityCheck` to `false`). `InspectorLauncher.open()`
needs neither — it turns that check off for its own controller, at the cost of 120 Hz rendering in
the inspector.

## Web viewer

For inspecting from a desktop browser, start the embedded server:

```kotlin
val url = WebViewer.start()   // loopback, port 9394; returns the URL to open
WebViewer.stop()
```

It streams the same timeline over SSE and runs on Android and iOS. See
[Web viewer](web-viewer.md) for bind modes, LAN access and tokens.
