package com.snoop.ui.internal

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun ClipEntry.plainText(): String? = getPlainText()
