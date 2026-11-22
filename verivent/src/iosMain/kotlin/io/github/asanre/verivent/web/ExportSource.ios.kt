package io.github.asanre.verivent.web

import io.github.asanre.verivent.core.InternalVeriventApi

import platform.Foundation.NSBundle

@InternalVeriventApi
actual val exportSource: SourceDto? by lazy {
    SourceDto(
        platform = "ios",
        appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String,
        veriventVersion = VERIVENT_VERSION,
    )
}
