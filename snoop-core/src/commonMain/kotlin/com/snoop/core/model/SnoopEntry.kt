package com.snoop.core.model

import kotlin.time.Instant

/** A single item shown in the unified Snoop timeline: either a network call or an analytics event. */
sealed interface SnoopEntry {
    val id: String
    val timestamp: Instant
}

/** Fixed per-entry allowance for the fields the byte estimate doesn't walk (ids, timestamps, refs). */
internal const val ENTRY_OVERHEAD_BYTES = 256
