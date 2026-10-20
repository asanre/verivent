package io.github.asanre.verivent.analytics

import kotlinx.serialization.json.JsonObject

/**
 * An event's properties as the plan checks them: their JSON, plus what is known about a top-level
 * property beyond it. Built from the host's values at capture ([hostValues]) or from captured ones
 * afterwards ([exportedValues]); nested values never carry a [Fact].
 */
internal class CheckedValues(val json: JsonObject, val facts: Map<String, Fact> = emptyMap()) {

    /** The JSON type of the top-level property [key], as the export carries it. */
    fun typeOf(key: String): String? = (facts[key] as? Fact.Foreign)?.type ?: json[key]?.let(::jsonType)

    /**
     * These properties redacted: from now on only checked for presence and type, since any other
     * verdict would reveal the value.
     */
    fun hiding(names: Set<String>): CheckedValues = if (names.isEmpty()) this else CheckedValues(
        json,
        facts + names.filter { it in json }.associateWith { name ->
            (facts[name] as? Fact.Foreign)?.let { Fact.Opaque(it.type) } ?: Fact.Hidden
        },
    )
}

internal sealed interface Fact {
    /** Redacted, value at hand: checked for presence and type all the way down, nothing else. */
    data object Hidden : Fact

    /** Only the type is known: a redacted value read back from an export, or a redacted [Foreign] one. */
    data class Opaque(val type: String) : Fact

    /** A value JSON has no type for, checked as its `toString()` but reported by its class. */
    data class Foreign(val type: String) : Fact

    /** Exported before types were: only checked for presence. */
    data object Untyped : Fact
}
