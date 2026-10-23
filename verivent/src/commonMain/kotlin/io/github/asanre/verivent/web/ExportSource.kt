package io.github.asanre.verivent.web

import kotlinx.serialization.Serializable

/**
 * Where a session was captured. A file states it once, in the entry that opens it; a console line
 * states it on every event, because a console has no header to keep. [appVersion] is left out when
 * the app doesn't declare one.
 */
@Serializable
internal data class SourceDto(val platform: String, val appVersion: String? = null, val veriventVersion: String)

/** This process's source, or null where Verivent never writes an export (the JS validator). */
internal expect val exportSource: SourceDto?
