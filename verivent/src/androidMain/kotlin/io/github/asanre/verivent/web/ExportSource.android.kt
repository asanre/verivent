package io.github.asanre.verivent.web

import io.github.asanre.verivent.core.internal.appVersion

internal actual val exportSource: SourceDto? by lazy {
    SourceDto(platform = "android", appVersion = appVersion(), veriventVersion = VERIVENT_VERSION)
}
