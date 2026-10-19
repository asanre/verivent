package io.github.asanre.verivent.ios

import io.github.asanre.verivent.web.WebViewer

/**
 * The web viewer: an embedded HTTP server that serves the captured timeline as a live page, so a
 * native host inspects its events from a desktop browser.
 *
 * This is the only viewer the native package ships — the Compose inspector stays out of the binary,
 * and the page itself travels as an inlined string, not as a resource bundle.
 */
object NativeViewer {

    val isRunning: Boolean get() = WebViewer.isRunning

    /** The token every request must carry as `?t=`, or null when no viewer is running. */
    val token: String? get() = WebViewer.token

    val defaultPort: Int get() = WebViewer.DEFAULT_PORT

    /**
     * Start the viewer (idempotent). Returns the URL to open — the LAN one when reachable
     * off-device, otherwise the local one, token always included. The bind is asynchronous, so a
     * failure surfaces afterwards in the device console rather than from this call.
     *
     * @param token forces this exact token instead of a generated one. Every bind is gated,
     *   [NativeViewerBind.Local] included: loopback is shared with every other app on the device.
     */
    fun start(bind: NativeViewerBind, port: Int, token: String?): String =
        WebViewer.start(bind = bind.core, port = port, token = token)

    /** The URL to reach this device from another machine, or null on a [NativeViewerBind.Local] bind. */
    fun lanUrl(): String? = WebViewer.lanUrl()

    fun stop() {
        WebViewer.stop()
    }
}
