package io.github.asanre.verivent.export

import io.github.asanre.verivent.core.InternalVeriventApi

import io.github.asanre.verivent.core.internal.appVersion

@InternalVeriventApi
actual val exportSource: SourceDto? by lazy {
    SourceDto(platform = "android", appVersion = appVersion(), veriventVersion = VERIVENT_VERSION, session = processSession)
}
