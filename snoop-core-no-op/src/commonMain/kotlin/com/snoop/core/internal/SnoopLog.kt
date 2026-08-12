package com.snoop.core.internal

/** No-op mirror: the release build stays mute. */
object SnoopLog {

    fun warn(tag: String, message: String, throwable: Throwable? = null) = Unit

    fun info(tag: String, message: String) = Unit
}
