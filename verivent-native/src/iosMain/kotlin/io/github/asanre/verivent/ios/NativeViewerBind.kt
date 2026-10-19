package io.github.asanre.verivent.ios

import io.github.asanre.verivent.web.WebViewerBind

/**
 * Where the viewer listens. Mirrors `WebViewerBind` so the bridge keeps verivent out of its exported
 * surface; [Lan] and [Host] are reachable off-device and are always token-gated.
 */
sealed interface NativeViewerBind {
    /** Loopback only (`127.0.0.1`); open. Reachable from the iOS simulator as `localhost`. */
    data object Local : NativeViewerBind

    /** Every interface (`0.0.0.0`), reachable across the LAN. */
    data object Lan : NativeViewerBind

    /** A specific bind address you choose. Gated like [Lan]. */
    data class Host(val address: String) : NativeViewerBind
}

internal val NativeViewerBind.core: WebViewerBind
    get() = when (this) {
        NativeViewerBind.Local -> WebViewerBind.Local
        NativeViewerBind.Lan -> WebViewerBind.Lan
        is NativeViewerBind.Host -> WebViewerBind.Host(address)
    }
