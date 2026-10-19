package io.github.asanre.verivent.core

/** No-op mirror of the opt-in for Verivent's own modules. */
@RequiresOptIn(message = "Verivent's own modules use this; it isn't API for hosts.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class InternalVeriventApi
