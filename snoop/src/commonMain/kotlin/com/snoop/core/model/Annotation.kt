package com.snoop.core.model

import kotlin.jvm.JvmInline

/**
 * A semantic badge attached to an analytics event by the host's `annotate {}` rules. Open-ended:
 * hosts can mint their own (`Annotation("checkout")`); the well-known ones below get dedicated
 * styling in the UI, everything else renders neutral.
 */
@JvmInline
value class Annotation(val label: String) {
    companion object {
        val Ecommerce = Annotation("ecommerce")
        val Conversion = Annotation("conversion")
        val Consent = Annotation("consent")
        val ScreenView = Annotation("screen")

        /** Stamped by Snoop itself when a tracking plan is loaded and doesn't define the event. */
        val NotInPlan = Annotation("not in plan")
    }
}
