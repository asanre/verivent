package io.github.asanre.verivent.core.internal

internal actual fun platformLogWarn(message: String, throwable: Throwable?) {
    if (throwable == null) console.warn("Verivent $message") else console.warn("Verivent $message", throwable)
}

internal actual fun platformLogInfo(message: String) {
    console.info("Verivent $message")
}
