package io.github.asanre.verivent.web

/** No-op mirror of `WebViewer` for release builds: the web server never starts. */
object WebViewer {
    val isRunning: Boolean get() = false
    val url: String get() = "http://localhost:$DEFAULT_PORT"
    val token: String? get() = null
    fun lanUrl(): String? = null
    fun start(bind: WebViewerBind = WebViewerBind.Local, port: Int = DEFAULT_PORT, token: String? = null): String = url
    fun stop() = Unit
    const val DEFAULT_PORT: Int = 9394
}
