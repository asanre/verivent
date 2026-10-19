package io.github.asanre.verivent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** Full-screen host for the inspector, launched from the notification or `InspectorLauncher.open(context)`. */
class InspectorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Matches what the host app gets on API 35+, where edge-to-edge is forced process-wide, so
        // the inspector looks the same on every API level. InspectorScreen insets its own content.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { InspectorScreen(onClose = ::finish) }
    }
}
