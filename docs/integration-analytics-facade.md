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

## Correlating events with their transport

If some channels ship over an HTTP endpoint served by the **same Ktor client** Snoop already watches
(e.g. a GTM/collector call), mark it as analytics transport and Snoop links the events to the call:

```kotlin
install(Snoop) {
    markAsAnalyticsTransport { it.url.host == "collector.example.com" }
}
```

Events sent to vendor SDKs (Firebase) won't correlate — their transport is invisible — and that's
expected; they still show up on the timeline as events.
