package io.github.asanre.verivent.core.internal

/** The file's text, or null when it doesn't exist. */
internal expect fun readTextFile(path: String): String?

/** Writes [text] to [path], creating the missing parent directories. */
internal expect fun writeTextFile(path: String, text: String)

internal expect fun deleteFile(path: String)
