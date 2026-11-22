package io.github.asanre.verivent.sync

import io.github.asanre.verivent.core.internal.DiagnosticLog
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Connects this app to a Verivent workspace, so the team sees its sessions without anyone exporting a
 * file. While connected, the workspace's published tracking plan validates the events (unless the host
 * loaded its own), and every analytics event captured goes to the workspace, as redacted as the app
 * shows it. Network calls stay on the device. Nothing is sent until [connect] is called.
 *
 * It never throws into the app: failures go to Verivent's diagnostics log and are retried, and a
 * connection key the workspace revoked stops it.
 */
@OptIn(ExperimentalAtomicApi::class)
object VeriventSync {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, failure -> DiagnosticLog.warn(TAG, "the workspace connection stopped", failure) },
    )

    private val running = AtomicReference<Job?>(null)

    /**
     * Start sending to the workspace that [connection] names; a connection already running ends
     * first. Each run of the app is one session in the workspace, and connecting again in the same
     * run carries on with it. Copy [connection] from the workspace's Connect page; one that isn't a
     * connection is logged and changes nothing.
     */
    fun connect(connection: String) {
        val parsed = runCatching { parseConnection(connection) }
            .getOrElse { failure ->
                DiagnosticLog.warn(TAG, failure.message.orEmpty())
                return
            }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            syncHttpClient().use { client -> WorkspaceSync(parsed, client).run() }
        }
        running.exchange(job)?.cancel()
        job.start()
    }

    /** Stop sending. Events already sent stay in the workspace. */
    fun disconnect() {
        running.exchange(null)?.cancel()
    }

    private const val TAG = "sync"
}

/** A client of its own, without Verivent's capture plugin, so these calls never reach the timeline. */
internal expect fun syncHttpClient(): HttpClient
