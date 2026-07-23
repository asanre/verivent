package com.snoop.web

/** No-op mirror of `snoop-web` for release builds: the web server never starts. */
object SnoopWebServer {
    val isRunning: Boolean get() = false
    val url: String get() = "http://localhost:$DEFAULT_PORT"
    val token: String? get() = null
    fun lanUrl(): String? = null
    fun start(port: Int = DEFAULT_PORT, host: String = "127.0.0.1", token: String? = null): String = url
    fun stop() = Unit
    const val DEFAULT_PORT: Int = 9394
}
