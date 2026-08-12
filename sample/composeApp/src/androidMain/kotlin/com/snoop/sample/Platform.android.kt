package com.snoop.sample

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.snoop.ui.Snoop
import com.snoop.web.SnoopWebServer
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun sampleEngine(): HttpClientEngineFactory<*> = CIO

private val PORT = SnoopWebServer.DEFAULT_PORT
internal actual val webViewerHint: String =
    "From your Mac: adb forward tcp:$PORT tcp:$PORT, then open the URL above — its ?t= token is required."

@Composable
actual fun PlatformExtras() {
    val context = LocalContext.current
    OutlinedButton(onClick = { Snoop.showNotification(context) }, modifier = Modifier.fillMaxWidth()) {
        Text("Show Snoop notification")
    }
}
