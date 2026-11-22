# Connect a workspace

Without it, a session reaches the team when someone exports it from the inspector and shares the
file. With `verivent-sync`, the app sends each analytics event to the team's workspace as it is
captured, and the workspace hands the app the plan it published. Nothing is sent until you connect.

## Add it

```kotlin
debugImplementation("io.github.asanre:verivent-sync:0.12.0")
releaseImplementation("io.github.asanre:verivent-sync-no-op:0.12.0")
```

Copy the connection from the workspace's **Connect** page and connect when the app starts:

```kotlin
VeriventSync.connect("https://<api key>:<connection key>@<project>")
```

Each `connect` starts a new session in the workspace. `VeriventSync.disconnect()` stops sending;
what was sent stays.

### Native iOS

The Swift package carries it too. It has no release mirror, so connect in debug builds only:

```swift
#if DEBUG
try? Verivent.connect("https://<api key>:<connection key>@<project>")
#endif
```

`Verivent.disconnect()` stops sending.

## What leaves the device

- Analytics events, as the inspector shows them: properties the app redacts arrive redacted. Redact
  anything personal before connecting (see [Filters, redaction & annotations](filters-redaction-annotations.md)).
- The platform, the app's version and Verivent's version, once per session.
- Never network calls.

The workspace keeps a session for 30 days after its last event.

## The plan

While connected, the latest version the workspace published validates every event, so the device
and the workspace agree. A plan the app loads itself with `Inspector.loadTrackingPlan` wins over it,
in that process. The workspace's plan isn't kept in `storageDir`: it is handed over again on every
start. An event that only exists in the workspace's draft shows as not in the plan on the device;
the workspace can check the session against the draft.

## The connection key

It ships inside the debug build, so treat it as public: it can only add events to that workspace
and read its published plan. A member revokes it on the Connect page and the app stops sending at
once. The workspace caps how much it takes: 200 events a call, 5,000 a session and 20,000 an hour.

## When something fails

The app never sees it. Failures go to Verivent's diagnostics log (Logcat, tag `Verivent`), events
wait and are sent again later, up to every five minutes. The buffer still holds 500 entries, so a
long outage drops the oldest. A revoked key stops the connection until the next `connect`.
