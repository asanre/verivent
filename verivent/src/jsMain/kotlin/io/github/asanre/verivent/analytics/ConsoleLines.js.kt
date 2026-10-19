package io.github.asanre.verivent.analytics

internal actual fun platformConsole(line: String) {
    console.log(line)
}

// The JS console doesn't truncate.
internal actual val consoleMaxBytes: Int = Int.MAX_VALUE
