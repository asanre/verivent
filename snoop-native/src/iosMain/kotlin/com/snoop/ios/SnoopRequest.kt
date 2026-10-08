package com.snoop.ios

/**
 * A request as `URLSession` described it, handed to the filter in [SnoopNetwork.configure].
 *
 * Mirrors nothing in snoop on purpose: the bridge owns every type in its public surface, the
 * same way [SnoopEvent] does for analytics. [url] is the raw one — a host's predicate should see
 * what was actually requested; redaction applies to what gets stored.
 */
data class SnoopRequest(
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val headers: Map<String, String>,
)
