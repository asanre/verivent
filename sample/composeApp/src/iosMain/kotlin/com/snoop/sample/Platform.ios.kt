package com.snoop.sample

import androidx.compose.runtime.Composable
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun sampleEngine(): HttpClientEngineFactory<*> = Darwin

internal actual val webViewerHint: String =
    "Simulator: open http://localhost:9394 on this Mac. Physical device: not reachable from a desktop (loopback)."

@Composable
actual fun PlatformExtras() {
    // No extra platform controls on iOS; the inspector is embedded via SnoopScreen.
}
