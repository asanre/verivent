package com.snoop.ios

import com.snoop.core.SnoopCore
import com.snoop.core.capture.BodyCapture
import com.snoop.core.internal.runSafely
import com.snoop.core.model.BodyPreview
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.redaction.RedactionEngine
import kotlin.concurrent.Volatile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * **Experimental.** Network ingestion for native iOS hosts, fed by the Swift `SnoopURLProtocol`.
 * The shape of this surface may still change in a minor release.
 *
 * The three capture calls mirror the Ktor plugin exactly — [begin] records a pending transaction,
 * [complete] or [fail] patches it by id — so a call looks the same in the viewer whichever adapter
 * captured it. Redaction runs on snoop's own engine, so the defaults (`Authorization`,
 * `Cookie`, the secret-ish query parameters, the `██` placeholder, fail-closed predicates) are the
 * ones Android already gets.
 */
object SnoopNetwork {

    private class Config(
        val maxContentLength: Long,
        val redaction: RedactionEngine,
        val keepRequest: ((SnoopRequest) -> Boolean)?,
    )

    /**
     * One immutable snapshot swapped in a single write: configured from the host's setup thread,
     * read from whatever queue URLSession happens to call back on.
     */
    @Volatile
    private var config = Config(DEFAULT_MAX_CONTENT_LENGTH, RedactionEngine(), null)

    /** The limit a host gets without picking one — the 250 KB the Ktor plugin also defaults to. */
    val defaultMaxContentLength: Long get() = DEFAULT_MAX_CONTENT_LENGTH

    /** The limit in force, so the Swift layer sizes its accumulator without restating the default. */
    val maxContentLength: Long get() = config.maxContentLength

    /**
     * Install body limit, redaction and filtering. Every argument is optional — nulls capture
     * everything and redact only the built-in defaults.
     *
     * Unlike the Ktor DSL, whose lists are additive, each call **replaces** the previous
     * configuration: there is no DSL here to accumulate into. The closures are held until the next
     * call — in practice for the life of the process — so capture `[weak self]` if one reaches into
     * an object you expect to be deallocated.
     */
    fun configure(
        maxContentLength: Long,
        redactHeader: ((String) -> Boolean)?,
        redactQueryParameter: ((String) -> Boolean)?,
        redactBody: ((String) -> String)?,
        keepRequest: ((SnoopRequest) -> Boolean)?,
    ) {
        config = Config(
            maxContentLength = maxContentLength,
            redaction = RedactionEngine(
                headerPredicates = listOfNotNull(redactHeader),
                queryParamPredicates = listOfNotNull(redactQueryParameter),
                bodyTransforms = listOfNotNull(redactBody),
            ),
            keepRequest = keepRequest,
        )
    }

    /**
     * Record a request on its way out and return the id its response must be reported under, or
     * null when it isn't being captured — Snoop disabled, or a filter said no. A null id means the
     * caller skips [complete] and [fail] entirely.
     *
     * @param bodyByteCount size of the whole body, which may exceed [body] when the caller only
     *   kept a prefix.
     * @param bodyPlaceholder stands in for a body the caller declined to read, e.g. an upload
     *   stream that can't be rewound; when set, [body] is ignored.
     */
    fun begin(
        method: String,
        url: String,
        host: String,
        path: String,
        contentType: String?,
        headers: Map<String, String>,
        body: NSData?,
        bodyByteCount: Long,
        bodyPlaceholder: String?,
    ): String? {
        var id: String? = null
        runSafely("ios.network.request") {
            val current = config
            if (!SnoopCore.enabled) return@runSafely
            if (current.keepRequest?.invoke(SnoopRequest(method, url, host, path, headers)) == false) {
                return@runSafely
            }
            val callId = SnoopCore.nextId()
            SnoopCore.recordNetwork(
                NetworkTransaction(
                    id = callId,
                    timestamp = SnoopCore.now(),
                    method = method,
                    url = current.redaction.redactUrl(url),
                    host = host,
                    path = path,
                    requestContentType = contentType,
                    requestHeaders = headers.toKeyValues(current.redaction),
                    requestBody = current.previewOf(body, bodyByteCount, contentType, bodyPlaceholder),
                ),
            )
            id = callId
        }
        return id
    }

    /** Patch the transaction [id] with its response. */
    fun complete(
        id: String,
        status: Int,
        protocol: String?,
        contentType: String?,
        headers: Map<String, String>,
        body: NSData?,
        bodyByteCount: Long,
        durationMillis: Long,
    ) {
        runSafely("ios.network.response") {
            val current = config
            SnoopCore.updateNetwork(id) {
                it.copy(
                    status = status,
                    protocol = protocol,
                    responseContentType = contentType,
                    responseHeaders = headers.toKeyValues(current.redaction),
                    responseBody = current.previewOf(body, bodyByteCount, contentType, null),
                    durationMillis = durationMillis,
                )
            }
        }
    }

    /**
     * Patch the transaction [id] with a transport failure. [message] goes through URL redaction
     * because `NSError.localizedDescription` embeds the full URL, query string and all — the same
     * reason the Ktor plugin redacts its failure messages.
     *
     * Unlike Ktor, which leaves a failed entry without a duration, this records one: the Swift side
     * always has the start mark. Deliberate, not an oversight.
     */
    fun fail(id: String, message: String, durationMillis: Long) {
        runSafely("ios.network.record-failure") {
            val current = config
            SnoopCore.updateNetwork(id) {
                it.copy(error = current.redaction.redactUrl(message), durationMillis = durationMillis)
            }
        }
    }

    /**
     * `NSDictionary` has no order to preserve, so headers are sorted by name — deterministic beats
     * hash order in a viewer someone is reading. Foundation has already folded repeated headers
     * into one comma-joined value by the time they reach here.
     */
    private fun Map<String, String>.toKeyValues(redaction: RedactionEngine): List<KeyValue> =
        entries.sortedBy { it.key }.map { redaction.redactHeader(it.key, it.value) }

    /** Same composition the Ktor plugin applies: preview, then form fields, then host transforms. */
    private fun Config.previewOf(
        body: NSData?,
        byteCount: Long,
        contentType: String?,
        placeholder: String?,
    ): BodyPreview? {
        val preview = when {
            placeholder != null -> BodyPreview(placeholder, byteCount, false)
            body == null -> return null
            BodyCapture.isTextLike(contentType) ->
                BodyCapture.textPreview(body.toByteArray(), contentType, maxContentLength, byteCount)
            else -> BodyCapture.binaryPlaceholder(contentType, byteCount)
        }
        val formRedacted = if (contentType.isFormUrlEncoded()) {
            preview.copy(text = redaction.redactFormBody(preview.text))
        } else {
            preview
        }
        return formRedacted.copy(text = redaction.redactBody(formRedacted.text))
    }

    /** No Ktor `ContentType` on this side of the fence, so the media type is matched by hand. */
    private fun String?.isFormUrlEncoded(): Boolean =
        this?.substringBefore(';')?.trim()?.lowercase() == "application/x-www-form-urlencoded"

    private const val DEFAULT_MAX_CONTENT_LENGTH = 250_000L
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
