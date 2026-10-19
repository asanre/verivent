package io.github.asanre.verivent.analytics

import platform.Foundation.NSLog

// NSLog sits on the unified log, which cuts dynamic content at about 1 KB (Apple's figure is
// approximate, and the persisted copy keeps less); live readers like `log stream` see this much.
internal actual val consoleMaxBytes: Int = 900

// os_log is a variadic C macro that Kotlin/Native can't call; NSLog lands in the unified log too.
internal actual fun platformConsole(line: String) {
    NSLog("VeriventEvent " + line.replace("%", "%%"))
}
