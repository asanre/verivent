package io.github.asanre.verivent.web

import platform.Foundation.NSBundle

internal actual val exportSource: SourceDto? by lazy {
    SourceDto(
        platform = "ios",
        appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String,
        veriventVersion = VERIVENT_VERSION,
    )
}
