package io.github.asanre.verivent.sample

import androidx.compose.runtime.Composable
import io.ktor.client.engine.HttpClientEngineFactory

/** Ktor engine per platform: CIO on Android, Darwin on iOS. */
internal expect fun sampleEngine(): HttpClientEngineFactory<*>

/** Platform-only demo controls (e.g. the Android notification button). */
@Composable
expect fun PlatformExtras()

/** How to reach the web viewer from a desktop browser on this platform. */
internal expect val webViewerHint: String
