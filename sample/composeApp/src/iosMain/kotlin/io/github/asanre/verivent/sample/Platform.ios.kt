package io.github.asanre.verivent.sample

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.asanre.verivent.ui.InspectorLauncher
import io.github.asanre.verivent.web.WebViewer
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun sampleEngine(): HttpClientEngineFactory<*> = Darwin

internal actual val webViewerHint: String =
    "Open the URL above on this Mac — its ?t= token is required. " +
        "The simulator shares the host loopback; a physical device needs WebViewerBind.Lan."

@Composable
actual fun PlatformExtras() {
    OutlinedButton(onClick = { InspectorLauncher.open() }, modifier = Modifier.fillMaxWidth()) {
        Text("Open Verivent (modal)")
    }
    OutlinedButton(onClick = { InspectorLauncher.showNotification() }, modifier = Modifier.fillMaxWidth()) {
        Text("Show Verivent notification")
    }
}
