package io.github.asanre.verivent.ios

/**
 * An event as the host describes it, handed to the filter in [NativeEvents.configure].
 *
 * Mirrors `AnalyticsEvent` rather than reusing it: the bridge owns every type in its public surface
 * so nothing from verivent reaches the generated Objective-C header.
 */
data class NativeEvent(
    val name: String,
    val channel: String,
    val properties: Map<String, Any?>,
)
