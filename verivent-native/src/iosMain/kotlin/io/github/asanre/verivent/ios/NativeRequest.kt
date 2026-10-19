package io.github.asanre.verivent.ios

/**
 * A request as `URLSession` described it, handed to the filter in [NativeNetwork.configure].
 *
 * Mirrors nothing in verivent on purpose: the bridge owns every type in its public surface, the
 * same way [NativeEvent] does for analytics. [url] is the raw one — a host's predicate should see
 * what was actually requested; redaction applies to what gets stored.
 */
data class NativeRequest(
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val headers: Map<String, String>,
)
