package io.github.asanre.verivent.ui

import androidx.core.app.NotificationCompat
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class InspectorNotificationTest {

    @Test
    fun theNotificationStaysOffTheLockScreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val notification = InspectorLauncher.notification(context, listOf("GET /reset/abc"))

        assertEquals(NotificationCompat.VISIBILITY_SECRET, notification.visibility)
    }
}
