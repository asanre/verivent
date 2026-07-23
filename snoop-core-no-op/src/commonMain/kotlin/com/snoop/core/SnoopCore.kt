package com.snoop.core

/**
 * No-op mirror of the consumer-facing `SnoopCore` surface for release builds. Adapter-facing API
 * (`store`, `record*`) is intentionally not mirrored: reading captured entries from shared app
 * code is a debug-only affair.
 */
object SnoopCore {
    var enabled: Boolean = false
    fun configure(capacity: Int) = Unit
    fun reset() = Unit
}
