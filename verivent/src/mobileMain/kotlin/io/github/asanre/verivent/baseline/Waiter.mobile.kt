package io.github.asanre.verivent.baseline

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal actual fun pollInRealTime(timeout: Duration, condition: () -> Boolean): Boolean = runBlocking {
    withTimeoutOrNull(timeout) {
        while (!condition()) delay(50.milliseconds)
        true
    } ?: false
}
