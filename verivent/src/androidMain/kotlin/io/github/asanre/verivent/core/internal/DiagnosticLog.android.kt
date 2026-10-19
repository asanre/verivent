package com.snoop.core.internal

import android.util.Log

private const val LOGCAT_TAG = "Snoop"

// android.util.Log is an unimplemented stub on a plain JVM, so it throws inside the host's unit
// tests unless they opt into returnDefaultValues. Diagnostics must never be the thing that breaks
// a host build, so a failing log is dropped like any other capture-side failure.
internal actual fun platformLogWarn(message: String, throwable: Throwable?) {
    runCatching { if (throwable == null) Log.w(LOGCAT_TAG, message) else Log.w(LOGCAT_TAG, message, throwable) }
}

internal actual fun platformLogInfo(message: String) {
    runCatching { Log.i(LOGCAT_TAG, message) }
}
