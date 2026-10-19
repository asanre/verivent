package io.github.asanre.verivent.core.model

import kotlin.time.Instant

enum class NetworkState { Pending, Complete, Failed }

data class NetworkTransaction(
    override val id: String,
    override val timestamp: Instant,
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val requestContentType: String? = null,
    val requestHeaders: List<KeyValue> = emptyList(),
    val requestBody: BodyPreview? = null,
    val status: Int? = null,
    val responseContentType: String? = null,
    val responseHeaders: List<KeyValue> = emptyList(),
    val responseBody: BodyPreview? = null,
    val protocol: String? = null,
    val durationMillis: Long? = null,
    val error: String? = null,
) : TimelineEntry {
    val state: NetworkState
        get() = when {
            error != null -> NetworkState.Failed
            status == null -> NetworkState.Pending
            else -> NetworkState.Complete
        }

    /**
     * Approximate retained size, cached because the store re-checks its byte budget on every
     * capture. [copy] gets a fresh estimate, which is what makes a response body arriving count.
     */
    internal val approxBytes: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
        (
            ENTRY_OVERHEAD_BYTES + url.length +
                (requestBody?.text?.length ?: 0) + (responseBody?.text?.length ?: 0) +
                requestHeaders.sumOf { it.name.length + it.value.length } +
                responseHeaders.sumOf { it.name.length + it.value.length }
            ).toLong()
    }
}
