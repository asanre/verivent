package com.snoop.core.model

import kotlin.time.Instant

/** No-op mirror. A single item in the unified Snoop timeline. */
sealed interface SnoopEntry {
    val id: String
    val timestamp: Instant
}
