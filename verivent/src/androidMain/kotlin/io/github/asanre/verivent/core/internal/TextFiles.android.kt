package io.github.asanre.verivent.core.internal

import java.io.File

internal actual fun readTextFile(path: String): String? = File(path).takeIf { it.isFile }?.readText()

internal actual fun writeTextFile(path: String, text: String) {
    File(path).apply { parentFile?.mkdirs() }.writeText(text)
}

internal actual fun deleteFile(path: String) {
    File(path).delete()
}
