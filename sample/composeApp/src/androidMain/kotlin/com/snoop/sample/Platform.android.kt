package com.snoop.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.snoop.ui.Snoop
import com.snoop.web.SnoopWebServer
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun sampleEngine(): HttpClientEngineFactory<*> = CIO

@Composable
actual fun PlatformExtras() {
    val context = LocalContext.current
    var serverUrl by remember { mutableStateOf<String?>(if (SnoopWebServer.isRunning) SnoopWebServer.url else null) }

    OutlinedButton(onClick = { Snoop.showNotification(context) }, modifier = Modifier.fillMaxWidth()) {
        Text("Show Snoop notification")
    }
    OutlinedButton(
        onClick = {
            if (SnoopWebServer.isRunning) {
                SnoopWebServer.stop()
                serverUrl = null
            } else {
                serverUrl = SnoopWebServer.start()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (serverUrl != null) "Stop web viewer" else "Start web viewer")
    }
    serverUrl?.let {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Web viewer at $it", style = MaterialTheme.typography.labelMedium)
            Text(
                "From your Mac: adb forward tcp:9394 tcp:9394, then open http://localhost:9394",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
