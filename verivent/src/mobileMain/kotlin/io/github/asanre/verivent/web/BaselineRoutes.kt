package io.github.asanre.verivent.web

import io.github.asanre.verivent.analytics.asObject
import io.github.asanre.verivent.analytics.asString
import io.github.asanre.verivent.baseline.BaselineFile
import io.github.asanre.verivent.baseline.BaselineMismatch
import io.github.asanre.verivent.baseline.FlowRun
import io.github.asanre.verivent.baseline.FlowScope
import io.github.asanre.verivent.baseline.Waiter
import io.github.asanre.verivent.baseline.lastEvent
import io.github.asanre.verivent.core.model.PlanEvent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The baseline for a UI test in another process, an XCUITest say: the same [FlowRun] and waiting as
 * `Baseline.flow`, one call per part of the flow. The test runs the app's actions between calls and
 * sends the values it bound with each, as `{"variables": {…}}`. One flow at a time.
 */
internal fun Route.baselineRoutes() {
    post("/api/baseline/flow") {
        val record = call.request.queryParameters["record"]
        val run = try {
            if (record != null) FlowRun(record, file = null) else BaselineFile.parse(call.receiveLimited()).let { FlowRun(it.name, it) }
        } catch (invalid: IllegalArgumentException) {
            return@post call.respondText(invalid.message.orEmpty(), status = HttpStatusCode.BadRequest)
        }
        val timeout = call.request.queryParameters["timeout"]?.toLongOrNull()?.milliseconds ?: 5.seconds
        live.withLock { flow = LiveFlow(run, FlowScope(run, recording = record != null, Waiter.RealTime, timeout)) }
        call.respondText("ok")
    }
    post("/api/baseline/step") {
        val name = call.request.queryParameters["name"] ?: return@post call.respondText("name is missing", status = HttpStatusCode.BadRequest)
        call.drive { scope.beginStep(name); null }
    }
    post("/api/baseline/step/end") {
        call.drive { scope.endStep(); null }
    }
    post("/api/baseline/finish") {
        call.drive {
            scope.finish()
            flow = null
            if (run.isRecording) run.recording().toJson() else null
        }
    }
    post("/api/baseline/last") {
        call.verdict {
            val body = Json.parseToJsonElement(call.receiveLimited()).jsonObject
            val event = PlanEvent(body["name"].asString("name"), body["channel"].asString("channel"))
            val timeout = (body["timeout"] as? JsonPrimitive)?.content?.toLongOrNull()?.milliseconds ?: 5.seconds
            val variant = body["variant"]?.asString("variant")
            val where = body["where"]?.asObject("where").orEmpty()
            val check = withContext(Dispatchers.IO) { lastEvent(event, Waiter.RealTime, timeout, variant, where) }
            if ((body["plan"] as? JsonPrimitive)?.booleanOrNull == true) check.shouldMatchPlan()
            if ("property" in body) check.shouldHave(body["property"].asString("property"), body["value"])
            null
        }
    }
}

private class LiveFlow(val run: FlowRun, val scope: FlowScope)

// CIO serves calls concurrently and a flow is a sequence: one flow, driven one call at a time.
private val live = Mutex()
private var flow: LiveFlow? = null

/** Binds the call's variables, then runs [step] on the live flow, which ends there if it fails, as `Baseline.flow` does. */
private suspend fun ApplicationCall.drive(step: LiveFlow.() -> String?) {
    live.withLock {
        val current = flow ?: return respondText("no flow: start one with POST /api/baseline/flow", status = HttpStatusCode.Conflict)
        verdict(onFailure = { flow = null }) {
            val variables = receiveLimited().takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it).jsonObject["variables"] as? JsonObject }
            variables?.forEach { (name, value) -> current.scope.bind(name, value) }
            withContext(Dispatchers.IO) { current.step() }
        }
    }
}

/**
 * `{"passed": true}`, with the recording when there is one, or `{"passed": false, "report": …}` as
 * the Kotlin test's failure reads. A misuse, a step out of order say, answers 400 with its reason.
 */
private suspend fun ApplicationCall.verdict(onFailure: () -> Unit = {}, block: suspend () -> String?) {
    val result = try {
        val recording = block()
        buildJsonObject {
            put("passed", true)
            recording?.let { put("recording", it) }
        }
    } catch (failure: AssertionError) {
        onFailure()
        buildJsonObject {
            put("passed", false)
            put("report", (failure as? BaselineMismatch)?.message ?: failure.message)
        }
    } catch (invalid: IllegalArgumentException) {
        onFailure()
        return respondText(invalid.message.orEmpty(), status = HttpStatusCode.BadRequest)
    }
    respondText(result.toString(), ContentType.Application.Json)
}

/** A baseline is one flow's share of a plan, so it gets a plan's room. */
private suspend fun ApplicationCall.receiveLimited(): String = requireNotNull(receiveUpToPlanSize()) { "body too large" }
