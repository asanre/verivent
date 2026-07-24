package com.snoop.sample

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.snoop.ui.Snoop
import com.snoop.web.SnoopWebServer
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun sampleEngine(): HttpClientEngineFactory<*> = Darwin

internal actual val webViewerHint: String =
    "Simulator: open http://localhost:${SnoopWebServer.DEFAULT_PORT} on this Mac. " +
        "Physical device: use SnoopBind.Lan and open the shown lanUrl()."

@Composable
actual fun PlatformExtras() {
    OutlinedButton(onClick = { Snoop.showNotification() }, modifier = Modifier.fillMaxWidth()) {
        Text("Show Snoop notification")
    }
}
