package io.github.asanre.verivent.analytics

import android.util.Log

private const val LOGCAT_TAG = "VeriventEvent"

// Logcat caps a whole entry, tag and header included, at about 4 KB; this keeps a margin.
internal actual val consoleMaxBytes: Int = 3800

// Same reason as DiagnosticLog: android.util.Log is a throwing stub inside a host's JVM unit tests.
internal actual fun platformConsole(line: String) {
    runCatching { Log.i(LOGCAT_TAG, line) }
}
