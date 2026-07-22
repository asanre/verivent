package com.snoop.web

/** No-op mirror of `snoop-web` for release builds: the web server never starts. */
object SnoopWebServer {
    val isRunning: Boolean get() = false
    val url: String get() = "http://localhost:$DEFAULT_PORT"
    fun start(port: Int = DEFAULT_PORT): String = url
    fun stop() = Unit
    const val DEFAULT_PORT: Int = 9394
}
