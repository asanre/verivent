package com.snoop.sample

import androidx.compose.runtime.Composable
import com.snoop.web.SnoopWebServer
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun sampleEngine(): HttpClientEngineFactory<*> = Darwin

internal actual val webViewerHint: String =
    "Simulator: open http://localhost:${SnoopWebServer.DEFAULT_PORT} on this Mac. " +
        "Physical device: use SnoopBind.Lan and open the shown lanUrl()."

@Composable
actual fun PlatformExtras() {
    // No extra platform controls on iOS; the inspector is embedded via SnoopScreen.
}
