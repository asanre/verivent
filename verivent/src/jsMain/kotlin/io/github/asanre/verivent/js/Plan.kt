@file:OptIn(ExperimentalJsExport::class)

package io.github.asanre.verivent.js

import io.github.asanre.verivent.analytics.CheckedValues
import io.github.asanre.verivent.analytics.Finding
import io.github.asanre.verivent.analytics.TrackingPlan
import io.github.asanre.verivent.analytics.exportedValues
import io.github.asanre.verivent.analytics.parseObject
import io.github.asanre.verivent.web.EntryDto
import io.github.asanre.verivent.web.toKeyValue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The npm package's entry point: a tracking plan, checked with the device's own rules and messages.
 *
 * @throws Error with the same message the device gives when [json] isn't a valid plan.
 */
@JsExport
class Plan(json: String) {

    private val plan = TrackingPlan.parse(json)

    val version: String get() = plan.version

    val events: Array<PlannedEvent> get() = plan.summary.events.map { PlannedEvent(it.name, it.channel) }.toTypedArray()

    /**
     * What breaks the plan in an event whose properties are the JSON object [propertiesJson], or null
     * when the plan doesn't define it.
     */
    fun check(name: String, channel: String, propertiesJson: String): Array<Issue>? =
        plan.check(name, channel, CheckedValues(parseObject(propertiesJson, "the properties")))?.findings?.toIssues()

    /** Every analytics event of an exported session (the JSON array the exports write), checked again. */
    fun checkSession(exportJson: String): Array<SessionEvent> =
        session.decodeFromString(ListSerializer(EntryDto.serializer()), exportJson)
            .mapNotNull { entry ->
                val name = entry.name?.takeIf { entry.type == "analytics" } ?: return@mapNotNull null
                val channel = entry.channel ?: return@mapNotNull null
                val properties = entry.properties.orEmpty().map { it.toKeyValue() }
                val check = plan.check(name, channel, exportedValues(properties))
                SessionEvent(
                    id = entry.id,
                    name = name,
                    channel = channel,
                    verdict = when {
                        check == null -> "not_in_plan"
                        check.findings.isEmpty() -> "complies"
                        else -> "breaks"
                    },
                    issues = check?.findings.orEmpty().toIssues(),
                    untyped = properties.any { it.type == null },
                    variant = check?.variant,
                )
            }
            .toTypedArray()

    private companion object {
        val session = Json { ignoreUnknownKeys = true }
    }
}

@JsExport
class PlannedEvent(val name: String, val channel: String)

/**
 * [path] is the property, `.child` into objects and `[*]` into every item of a list; when it goes
 * through a list, [count] of [total] items break it. [line] reads as the device shows it.
 */
@JsExport
class Issue(val path: String, val message: String, val count: Int?, val total: Int?) {
    val line: String get() = "$path · " + if (count == null) message else "$message · $count of $total"
}

/**
 * [verdict] is `complies`, `breaks` or `not_in_plan`. [untyped] flags an export from before 0.9,
 * whose properties carry no type and so are only checked for presence. [variant] is the id of the
 * plan's variant the event was sent as, found again by this plan; null when the plan has no
 * variants for it, none fits, or the property that tells them apart is redacted.
 */
@JsExport
class SessionEvent(
    val id: String,
    val name: String,
    val channel: String,
    val verdict: String,
    val issues: Array<Issue>,
    val untyped: Boolean,
    val variant: String?,
)

private fun List<Finding>.toIssues(): Array<Issue> = map { Issue(it.path, it.message, it.count, it.total) }.toTypedArray()
