package io.github.asanre.verivent.core.internal

// The JS build only carries the validator for the npm package, which never keeps a plan on disk.

internal actual fun readTextFile(path: String): String? = null

internal actual fun writeTextFile(path: String, text: String) = Unit

internal actual fun deleteFile(path: String) = Unit
