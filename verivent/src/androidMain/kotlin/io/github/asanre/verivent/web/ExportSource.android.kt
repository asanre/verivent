package io.github.asanre.verivent.web

import io.github.asanre.verivent.core.internal.VeriventInitProvider

internal actual val exportSource: SourceDto? by lazy {
    SourceDto(platform = "android", appVersion = VeriventInitProvider.appVersion, veriventVersion = VERIVENT_VERSION)
}
