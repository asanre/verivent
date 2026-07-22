package com.snoop.sample

import androidx.compose.runtime.Composable
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun sampleEngine(): HttpClientEngineFactory<*> = Darwin

@Composable
actual fun PlatformExtras() {
    // No extra platform controls on iOS; the inspector is embedded via SnoopScreen.
}
