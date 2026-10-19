package io.github.asanre.verivent.analytics

/**
 * No-op mirror of the analytics sink in `verivent` with the identical public API for release builds. Create with
 * the [AnalyticsSink] factory.
 */
class AnalyticsSink internal constructor(@Suppress("UNUSED_PARAMETER") config: AnalyticsSinkConfig) {
    fun log(
        name: String,
        channel: String,
        properties: Map<String, Any?> = emptyMap(),
        sections: Map<String, Map<String, Any?>> = emptyMap(),
    ) = Unit
}

fun AnalyticsSink(configure: AnalyticsSinkConfig.() -> Unit = {}): AnalyticsSink =
    AnalyticsSink(AnalyticsSinkConfig().apply(configure))
