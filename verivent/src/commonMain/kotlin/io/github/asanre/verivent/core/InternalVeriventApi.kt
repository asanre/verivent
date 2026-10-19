package io.github.asanre.verivent.core

/**
 * Public only so Verivent's own modules can share it. Not API for hosts: it can change in any
 * release.
 */
@RequiresOptIn(message = "Verivent's own modules use this; it isn't API for hosts.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class InternalVeriventApi
