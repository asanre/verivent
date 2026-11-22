package io.github.asanre.verivent.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun syncHttpClient(): HttpClient = HttpClient(Darwin)
