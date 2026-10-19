package io.github.asanre.verivent.sample

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.asanre.verivent.ui.InspectorLauncher
import io.github.asanre.verivent.web.WebViewer
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun sampleEngine(): HttpClientEngineFactory<*> = CIO

private val PORT = WebViewer.DEFAULT_PORT
internal actual val webViewerHint: String =
    "From your Mac: adb forward tcp:$PORT tcp:$PORT, then open the URL above — its ?t= token is required."

@Composable
actual fun PlatformExtras() {
    val context = LocalContext.current
    OutlinedButton(onClick = { InspectorLauncher.showNotification(context) }, modifier = Modifier.fillMaxWidth()) {
        Text("Show Verivent notification")
    }
}
