@file:OptIn(InternalVeriventApi::class)

package io.github.asanre.verivent.sync

import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.InternalVeriventApi
import io.github.asanre.verivent.core.internal.DiagnosticLog
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.web.SourceDto
import io.github.asanre.verivent.web.exportEntryJson
import io.github.asanre.verivent.web.exportSource
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * One connection to a workspace for the life of [run]: first the published plan, then every analytics
 * event captured, in batches, under the [session] this run names. Whatever goes wrong is logged and
 * retried later; only a key the workspace no longer knows stops it.
 */
internal class WorkspaceSync(
    private val connection: Connection,
    private val client: HttpClient,
    private val session: String,
    private val interval: Duration = 5.seconds,
) {
    private val source: SourceDto = requireNotNull(exportSource) { "Verivent writes no sessions on this platform" }

    // Ids grow with every capture, so the highest one sent marks everything before it as sent.
    private var sentUpTo = 0L

    suspend fun run() {
        var planSettled = false
        var wait = Duration.ZERO
        while (true) {
            delay(wait)
            val step = if (planSettled) sendPending() else loadPlan().also { planSettled = it == Step.Next }
            wait = when (step) {
                Step.Next -> interval
                Step.Later -> (wait * 2).coerceIn(interval, MAX_WAIT)
                Step.Stop -> return
            }
        }
    }

    private suspend fun loadPlan(): Step = when (val reply = post("device_plan", buildJsonObject { put("key", connection.key) })) {
        is Reply.Answered -> {
            reply.body?.takeIf(::isPublished)?.let { plan ->
                runCatching { Inspector.installWorkspacePlan(plan) }
                    .onFailure { DiagnosticLog.warn(TAG, "the workspace's plan doesn't load; validating without it", it) }
            }
            Step.Next
        }
        Reply.Unreachable -> Step.Later
        Reply.Revoked -> Step.Stop
    }

    // A workspace with nothing published answers JSON null; anything else is handed to the plan parser.
    private fun isPublished(body: String) = runCatching { Json.parseToJsonElement(body) !is JsonNull }.getOrDefault(true)

    private suspend fun sendPending(): Step {
        val pending = Inspector.entriesSnapshot.filterIsInstance<AnalyticsEntry>().filter { idOf(it) > sentUpTo }
        for (batch in pending.chunked(BATCH)) {
            when (post("ingest_events", batchBody(batch))) {
                is Reply.Answered -> sentUpTo = idOf(batch.last())
                Reply.Unreachable -> return Step.Later
                Reply.Revoked -> return Step.Stop
            }
        }
        return Step.Next
    }

    private fun idOf(entry: AnalyticsEntry): Long = entry.id.toLongOrNull() ?: 0L

    private fun batchBody(batch: List<AnalyticsEntry>) = buildJsonObject {
        put("key", connection.key)
        put("session", session)
        put("platform", source.platform)
        put("app_version", source.appVersion)
        put("library_version", source.veriventVersion)
        putJsonArray("events") { batch.forEach { add(exportEntryJson(it)) } }
    }

    private suspend fun post(function: String, body: JsonObject): Reply {
        // The body is read here too: a connection can drop after the status arrived.
        val (status, text) = try {
            val response = client.post(connection.rpc(function)) {
                header("apikey", connection.apiKey)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            response.status.value to response.bodyAsText()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            DiagnosticLog.warn(TAG, "can't reach the workspace; trying again later", failure)
            return Reply.Unreachable
        }
        // The functions refuse a key with 401 and a malformed call with 400; anything else (a cap, a
        // project not deployed yet, a gateway) may let up, so it's tried again later.
        return when (status) {
            in 200..299 -> Reply.Answered(text)
            400 -> {
                DiagnosticLog.warn(TAG, "$function refused: $text")
                Reply.Answered(null)
            }
            401 -> {
                DiagnosticLog.warn(TAG, "the workspace doesn't know this connection key, or revoked it; stopped")
                Reply.Revoked
            }
            else -> Reply.Unreachable
        }
    }

    private sealed interface Reply {
        /** [body] is null when the call was refused: sending it again would be refused too. */
        class Answered(val body: String?) : Reply
        data object Unreachable : Reply
        data object Revoked : Reply
    }

    private enum class Step { Next, Later, Stop }

    private companion object {
        const val TAG = "sync"
        const val BATCH = 200
        val MAX_WAIT = 5.minutes
    }
}
