package com.snoop.core.model

import kotlin.jvm.JvmInline

/** No-op mirror of the analytics annotation badge; referenced by `annotate {}` lambdas. */
@JvmInline
value class Annotation(val label: String) {
    companion object {
        val Ecommerce = Annotation("ecommerce")
        val Conversion = Annotation("conversion")
        val Consent = Annotation("consent")
        val ScreenView = Annotation("screen")
    }
}
