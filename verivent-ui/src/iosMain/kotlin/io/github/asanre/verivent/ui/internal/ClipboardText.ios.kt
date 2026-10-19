package io.github.asanre.verivent.ui.internal

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun ClipEntry.plainText(): String? = getPlainText()
