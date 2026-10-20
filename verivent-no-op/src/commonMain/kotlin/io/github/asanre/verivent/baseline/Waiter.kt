package io.github.asanre.verivent.baseline

import kotlin.time.Duration

fun interface Waiter {

    fun until(timeout: Duration, condition: () -> Boolean): Boolean

    companion object {
        val RealTime: Waiter = Waiter { _, condition -> condition() }
    }
}
