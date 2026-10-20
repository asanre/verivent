package io.github.asanre.verivent.baseline

import kotlin.time.Duration

// One thread and no app to drive: nothing can arrive while this waits, so it only looks once.
internal actual fun pollInRealTime(timeout: Duration, condition: () -> Boolean): Boolean = condition()
