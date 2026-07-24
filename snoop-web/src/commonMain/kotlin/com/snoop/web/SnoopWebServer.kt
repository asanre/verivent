package com.snoop.web

import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlin.concurrent.atomics.AtomicReference

/**
 * A tiny embedded HTTP server that serves the Snoop timeline as a live web page, so you can inspect
 * traffic from a desktop browser while debugging.
 *
 * Binds loopback by default ([SnoopBind.Local]: `adb forward tcp:9394` on Android; `localhost:9394`
 * on the iOS simulator, which shares the host loopback). Pass [SnoopBind.Lan] to reach a physical
 * device from your Mac over Wi-Fi; those binds are always token-gated. Reads the same shared
 * [SnoopCore] store the on-device UI uses.
 */
object SnoopWebServer {

    private val running = AtomicReference<Running?>(null)

    val isRunning: Boolean get() = running.load() != null

    /** The URL for local access; carries the `?t=` token when the server is gated. */
    val url: String get() = urlOf(running.load())

    /** The access token every request must carry as `?t=`, or null when the server is open ([SnoopBind.Local]). */
    val token: String? get() = running.load()?.token

    /**
     * The URL to reach this device from another machine on the LAN, or null on a [SnoopBind.Local]
     * bind (nothing off-device) or when no LAN address is available. Includes the token.
     */
    fun lanUrl(): String? = lanUrlOf(running.load(), localHostAddress())

    /**
     * Start the server (idempotent, thread-safe). Returns the URL you'd actually open: [lanUrl]
     * when reachable off-device, otherwise the local [url] — token included when gated.
     *
     * @param bind where to listen. [SnoopBind.Local] (default) is loopback-only and open;
     *   [SnoopBind.Lan] and [SnoopBind.Host] are reachable off-device and always token-gated.
     * @param token forces this exact token instead of the generated one; ignored for [SnoopBind.Local],
     *   which is always open.
     */
    fun start(bind: SnoopBind = SnoopBind.Local, port: Int = DEFAULT_PORT, token: String? = null): String {
        val resolvedToken = resolveToken(bind, token)
        val candidate = runCatching { createServer(port, bind.bindAddress, resolvedToken) }.getOrNull() ?: return url
        val started = Running(candidate, port, bind, resolvedToken)
        if (!running.compareAndSet(null, started)) return url
        // Bind failure (port in use, restricted env) must never crash the host app.
        runCatching { candidate.start(wait = false) }
            .onFailure { running.compareAndSet(started, null) }
        return lanUrlOf(started, localHostAddress()) ?: urlOf(started)
    }

    fun stop() {
        val started = running.exchange(null) ?: return
        started.server.stop(gracePeriodMillis = 300, timeoutMillis = 1000)
    }

    internal fun createServer(port: Int, host: String = LOOPBACK, token: String? = null): EmbeddedServer<*, *> =
        embeddedServer(CIO, host = host, port = port) { snoopModule(token) }

    const val DEFAULT_PORT: Int = 9394
    internal const val LOOPBACK: String = "127.0.0.1"
}

/** Everything that describes a live server, swapped in and out as one value so it can't half-update. */
internal data class Running(
    val server: EmbeddedServer<*, *>,
    val port: Int,
    val bind: SnoopBind,
    val token: String?,
)
