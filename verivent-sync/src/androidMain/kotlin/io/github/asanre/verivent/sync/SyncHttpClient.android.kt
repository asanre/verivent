package io.github.asanre.verivent.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android

internal actual fun syncHttpClient(): HttpClient = HttpClient(Android)
