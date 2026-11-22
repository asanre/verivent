package io.github.asanre.verivent.web

import io.github.asanre.verivent.core.InternalVeriventApi
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Where a session was captured. A file states it once, in the entry that opens it; a console line
 * states it on every event, because a console has no header to keep. [appVersion] is left out when
 * the app doesn't declare one.
 */
@OptIn(ExperimentalSerializationApi::class)
@InternalVeriventApi
@Serializable
data class SourceDto(
    val platform: String,
    val appVersion: String? = null,
    val veriventVersion: String,
    /** This run of the app: its exports, its console lines and its workspace session all name it. */
    val session: String? = null,
    /** The session format, written even when it's the default; files from before it carry none. */
    @EncodeDefault val schema: Int = SESSION_SCHEMA,
)

private const val SESSION_SCHEMA = 1

/** One per process, made when it's first read. */
@OptIn(ExperimentalUuidApi::class)
internal val processSession: String by lazy { Uuid.random().toString() }

/** This process's source, or null where Verivent never writes an export (the JS validator). */
@InternalVeriventApi
expect val exportSource: SourceDto?
