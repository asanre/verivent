package io.github.asanre.verivent.web

/**
 * Where [WebViewer] listens.
 *
 * [Local] is loopback only — reach it with `adb forward` or the iOS simulator. [Lan] and [Host] are
 * reachable from other machines. Every mode is token-gated (loopback is shared with every other app
 * on the device): a generated token unless you pass your own to [WebViewer.start].
 */
sealed interface WebViewerBind {
    /** Loopback only (`127.0.0.1`). */
    data object Local : WebViewerBind

    /** Every interface (`0.0.0.0`), reachable across the LAN. */
    data object Lan : WebViewerBind

    /** A specific bind address you choose (e.g. one interface's IP). */
    data class Host(val address: String) : WebViewerBind
}

internal val WebViewerBind.bindAddress: String
    get() = when (this) {
        WebViewerBind.Local -> WebViewer.LOOPBACK
        WebViewerBind.Lan -> "0.0.0.0"
        is WebViewerBind.Host -> address
    }

/** Loopback is unreachable from another machine, so it has no LAN URL to offer. */
internal val WebViewerBind.isLoopbackOnly: Boolean get() = this == WebViewerBind.Local
