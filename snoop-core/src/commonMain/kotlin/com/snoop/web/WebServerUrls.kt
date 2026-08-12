package com.snoop.web

/** The URL for local access; carries the token as `?t=` when the server is gated. */
internal fun urlOf(running: Running?): String {
    val base = "http://localhost:${running?.port ?: SnoopWebServer.DEFAULT_PORT}"
    return running?.token?.let { "$base/?t=$it" } ?: base
}

/** The off-device URL, or null on a [SnoopBind.Local] bind or when no LAN address is available. */
internal fun lanUrlOf(running: Running?, address: String?): String? {
    if (running == null || running.bind.isLoopbackOnly || address == null) return null
    val base = "http://$address:${running.port}"
    return running.token?.let { "$base/?t=$it" } ?: base
}
