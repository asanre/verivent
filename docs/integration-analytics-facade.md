# Hook your analytics facade

Snoop can't see analytics events by inspecting network traffic — vendor SDKs like Firebase send over
their own opaque transport. The one place every event is visible is your app's **analytics facade**,
the single function all events flow through before they fan out to vendors. Wire Snoop there.

## The idea

If your app has a chokepoint like this:

```kotlin
interface AnalyticsHelper {
    fun log(event: AnalyticsEvent)
}

class BaseAnalyticsHelper(
    private val dispatchers: List<AnalyticsDispatcher>,   // Firebase, server-side, GTM, ...
) : AnalyticsHelper {
    override fun log(event: AnalyticsEvent) {
        dispatchers.forEach { it.dispatch(event) }        // <-- every event passes here
    }
}
```

wrap it with a decorator that also forwards to Snoop, gated to internal/debug builds:

```kotlin
class SnoopAnalyticsHelper(
    private val delegate: AnalyticsHelper,
    private val snoop: SnoopAnalytics = SnoopAnalytics {
        sanitizeProperty { it in setOf("user_email", "user_id") }
        annotate { if (it.name in ecommerceEvents) listOf(Annotation.Ecommerce) else emptyList() }
    },
) : AnalyticsHelper {
    override fun log(event: AnalyticsEvent) {
        delegate.log(event)
        snoop.log(
            name = event.name,
            channel = event.channelLabel(),      // map your sealed event type -> "firebase" / "gtm" / ...
            properties = event.properties,
        )
    }
}
```

Bind `SnoopAnalyticsHelper` in place of `BaseAnalyticsHelper` only in the internal build variant; the
production variant binds the plain helper and pulls the `snoop-analytics-no-op` artifact, so this
decorator can stay in shared code and compile to nothing in release.

## Channels

`channel` is a free-form string. Use it to separate transports (`"firebase"`, `"gtm"`,
`"server-side"`); the UI groups, filters and colors by it. Derive it from your own event type:

```kotlin
private fun AnalyticsEvent.channelLabel() = when (this) {
    is FirebaseEvent -> "firebase"
    is ServerSideEvent -> "server-side"
    is GtmEvent -> "gtm"
}
```

## User properties

Providers such as Firebase attach the current user properties to every hit, and DebugView shows them
next to each event. Pass the same snapshot as `userProperties` and the event detail shows it as a
separate *User properties* block, hidden when empty. Snoop keeps no user state of its own, so take
the snapshot where the event is sent:

```kotlin
snoop.log(
    name = event.name,
    channel = "firebase",
    properties = event.properties,
    userProperties = currentUserProperties.toMap(),
)
```

`sanitizeProperty` redacts user properties too.
