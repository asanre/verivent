package io.github.asanre.verivent.web

/** The URL for local access, token included; the bare default port when nothing is running. */
internal fun urlOf(running: Running?): String {
    running ?: return "http://localhost:${WebViewer.DEFAULT_PORT}"
    return "http://localhost:${running.port}/?t=${running.token}"
}

/** The off-device URL, or null on a [WebViewerBind.Local] bind or when no LAN address is available. */
internal fun lanUrlOf(running: Running?, address: String?): String? {
    if (running == null || running.bind.isLoopbackOnly || address == null) return null
    return "http://$address:${running.port}/?t=${running.token}"
}
