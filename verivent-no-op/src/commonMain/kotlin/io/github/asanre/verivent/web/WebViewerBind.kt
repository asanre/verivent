package io.github.asanre.verivent.web

/** No-op mirror of `WebViewer`'s bind modes. */
sealed interface WebViewerBind {
    data object Local : WebViewerBind
    data object Lan : WebViewerBind
    data class Host(val address: String) : WebViewerBind
}
