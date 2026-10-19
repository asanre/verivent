package com.snoop.ios

import com.snoop.web.SnoopWebServer

/**
 * The web viewer: an embedded HTTP server that serves the captured timeline as a live page, so a
 * native host inspects its events from a desktop browser.
 *
 * This is the only viewer the native package ships — the Compose inspector stays out of the binary,
 * and the page itself travels as an inlined string, not as a resource bundle.
 */
object SnoopViewer {

    val isRunning: Boolean get() = SnoopWebServer.isRunning

    /** The token every request must carry as `?t=`, or null when no viewer is running. */
    val token: String? get() = SnoopWebServer.token

    val defaultPort: Int get() = SnoopWebServer.DEFAULT_PORT

    /**
     * Start the viewer (idempotent). Returns the URL to open — the LAN one when reachable
     * off-device, otherwise the local one, token always included. The bind is asynchronous, so a
     * failure surfaces afterwards in the device console rather than from this call.
     *
     * @param token forces this exact token instead of a generated one. Every bind is gated,
     *   [SnoopViewerBind.Local] included: loopback is shared with every other app on the device.
     */
    fun start(bind: SnoopViewerBind, port: Int, token: String?): String =
        SnoopWebServer.start(bind = bind.core, port = port, token = token)

    /** The URL to reach this device from another machine, or null on a [SnoopViewerBind.Local] bind. */
    fun lanUrl(): String? = SnoopWebServer.lanUrl()

    fun stop() {
        SnoopWebServer.stop()
    }
}
