package io.github.asanre.verivent.core.model

import kotlin.time.Instant

/** No-op mirror. A single item in the unified Verivent timeline. */
sealed interface TimelineEntry {
    val id: String
    val timestamp: Instant
}
