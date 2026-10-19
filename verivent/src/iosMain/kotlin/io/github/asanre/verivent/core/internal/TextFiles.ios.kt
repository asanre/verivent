@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.asanre.verivent.core.internal

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringByDeletingLastPathComponent
import platform.Foundation.writeToFile

internal actual fun readTextFile(path: String): String? =
    if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
        NSString.create(contentsOfFile = path, encoding = NSUTF8StringEncoding, error = null)?.toString()
    } else {
        null
    }

internal actual fun writeTextFile(path: String, text: String) {
    val directory = (path as NSString).stringByDeletingLastPathComponent
    NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    check((text as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)) {
        "could not write $path"
    }
}

internal actual fun deleteFile(path: String) {
    NSFileManager.defaultManager.removeItemAtPath(path, error = null)
}
