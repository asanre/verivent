package io.github.asanre.verivent.web

import io.github.asanre.verivent.core.internal.DiagnosticLog
import io.ktor.server.application.serverConfig
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val TAG = "web"

/**
 * A tiny embedded HTTP server that serves the Verivent timeline as a live web page, so you can inspect
 * traffic from a desktop browser while debugging.
 *
 * Binds loopback by default ([WebViewerBind.Local]: `adb forward tcp:9394` on Android; `localhost:9394`
 * on the iOS simulator, which shares the host loopback). Pass [WebViewerBind.Lan] to reach a physical
 * device from your Mac over Wi-Fi. Every bind is token-gated — loopback included, since it is shared
 * with every other app on the device. Reads the same shared [Inspector] store the on-device UI uses.
 */
object WebViewer {

    private val running = AtomicReference<Running?>(null)

    val isRunning: Boolean get() = running.load() != null

    /** The URL for local access, token included. */
    val url: String get() = urlOf(running.load())

    /** The access token every request must carry as `?t=`, or null when no server is running. */
    val token: String? get() = running.load()?.token

    /**
     * The URL to reach this device from another machine on the LAN, or null on a [WebViewerBind.Local]
     * bind (nothing off-device) or when no LAN address is available. Includes the token.
     */
    fun lanUrl(): String? = lanUrlOf(running.load(), localHostAddress())

    /**
     * Start the server (idempotent, thread-safe). Returns the URL you'd actually open: [lanUrl]
     * when reachable off-device, otherwise the local [url], token included. When the server can't
     * even be created nothing runs: [isRunning] stays false and this returns [url]'s bare default,
     * with no token.
     *
     * The bind itself is asynchronous: this returns before the port is actually listening, so a
     * failure (port taken, restricted environment) surfaces afterwards as a warning in Logcat/the
     * device console, with [isRunning] flipped back to false. The same URL is logged on success, so
     * the token is always recoverable from the log. A call that races a live or still-stopping
     * server is discarded and returns that server's URL — [isRunning] is the source of truth.
     *
     * @param bind where to listen. [WebViewerBind.Local] (default) is loopback-only; [WebViewerBind.Lan] and
     *   [WebViewerBind.Host] are reachable off-device. All of them are token-gated.
     * @param token forces this exact token instead of the generated one. A blank one is replaced
     *   by a generated token, since it would let in requests that carry none.
     */
    fun start(bind: WebViewerBind = WebViewerBind.Local, port: Int = DEFAULT_PORT, token: String? = null): String {
        val resolvedToken = resolveToken(token)
        val scope = CoroutineScope(SupervisorJob())
        // The engine's failure handler has to reach the [Running] it belongs to, which only exists
        // once the engine is built — so it reads the slot instead of capturing the value.
        val slot = AtomicReference<Running?>(null)
        val candidate = runCatching {
            createServer(port, bind.bindAddress, resolvedToken, scope) { cause ->
                DiagnosticLog.warn(TAG, "the web server engine failed", cause)
                slot.load()?.let { discard(it) }
            }
        }.onFailure {
            DiagnosticLog.warn(TAG, "could not create the web server", it)
            scope.cancel()
        }.getOrNull() ?: return url
        val started = Running(candidate, scope, port, bind, resolvedToken)
        slot.store(started)
        // Losing the race means another start() won: this engine is nobody's, so it has to go.
        if (!running.compareAndSet(null, started)) {
            DiagnosticLog.warn(TAG, "start(port=$port) discarded: another server is live or still stopping")
            started.shutdown(graceMillis = 0, timeoutMillis = 0)
            return url
        }
        // Bind failure (port in use, restricted env) must never crash the host app.
        val launched = runCatching { candidate.start(wait = false) }
            .onFailure {
                DiagnosticLog.warn(TAG, "the web server could not start", it)
                discard(started)
            }
        val opened = lanUrlOf(started, localHostAddress()) ?: urlOf(started)
        if (launched.isSuccess) DiagnosticLog.info(TAG, "viewer at $opened")
        return opened
    }

    fun stop() {
        // Stop the engine *before* clearing the slot: an immediate restart that saw an empty slot
        // would race a port this one has not released yet.
        val started = running.load() ?: return
        started.shutdown(graceMillis = 300, timeoutMillis = 1000)
        running.compareAndSet(started, null)
    }

    /**
     * Retire a server that never became (or stopped being) the live one, slot and scope included.
     * Idempotent through the CAS, which matters because a bind failure arrives twice: as a throw
     * from `start()` and as an engine failure on the handler.
     *
     * The zero grace and timeout are load-bearing. One caller is the engine's own exception
     * handler, and `stop()` there would join the job it is running inside; only a non-positive
     * timeout takes the `withTimeoutOrNull` path that returns without joining. Anything above zero
     * buys a stall inside the handler.
     */
    private fun discard(started: Running) {
        if (running.compareAndSet(started, null)) started.shutdown(graceMillis = 0, timeoutMillis = 0)
    }

    /** @param token null leaves the server ungated — only the gate's own tests do that. */
    internal fun createServer(
        port: Int,
        host: String = LOOPBACK,
        token: String? = null,
        scope: CoroutineScope = CoroutineScope(SupervisorJob()),
        onEngineFailure: (Throwable) -> Unit = {},
    ): EmbeddedServer<*, *> = embeddedServer(
        CIO,
        serverConfig {
            // The engine outlives start(): a bind failure throws there *and* lands here, and
            // anything that fails afterwards only lands here. Without a handler it reaches the
            // default one, which on Kotlin/Native takes the whole host process down.
            parentCoroutineContext = scope.coroutineContext +
                CoroutineExceptionHandler { _, cause -> onEngineFailure(cause) }
            module { webViewerModule(token) }
        },
    ) {
        connectors += EngineConnectorBuilder().apply {
            this.host = host
            this.port = port
        }
        // SO_REUSEADDR. Whatever the viewer page had open — the SSE stream, above all — lands in
        // TIME_WAIT on this port when stop() closes it, and lingers there for tens of seconds. The
        // immediate restart would lose the re-bind to those ghosts; a live listener still wins.
        reuseAddress = true
    }

    const val DEFAULT_PORT: Int = 9394
    internal const val LOOPBACK: String = "127.0.0.1"
}

/** Everything that describes a live server, swapped in and out as one value so it can't half-update. */
internal data class Running(
    val server: EmbeddedServer<*, *>,
    val scope: CoroutineScope,
    val port: Int,
    val bind: WebViewerBind,
    /** Never null: every bind is gated, so the URLs can always carry a token. */
    val token: String,
)

/** Stop the engine and release the scope it runs on; both leak otherwise. */
internal fun Running.shutdown(graceMillis: Long, timeoutMillis: Long) {
    runCatching { server.stop(gracePeriodMillis = graceMillis, timeoutMillis = timeoutMillis) }
        .onFailure { DiagnosticLog.warn(TAG, "the web server did not stop cleanly", it) }
    scope.cancel()
}
