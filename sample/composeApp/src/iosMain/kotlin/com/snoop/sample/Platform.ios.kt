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
    "Open the URL above on this Mac — its ?t= token is required. " +
        "The simulator shares the host loopback; a physical device needs SnoopBind.Lan."

@Composable
actual fun PlatformExtras() {
    OutlinedButton(onClick = { Snoop.showNotification() }, modifier = Modifier.fillMaxWidth()) {
        Text("Show Snoop notification")
    }
}
