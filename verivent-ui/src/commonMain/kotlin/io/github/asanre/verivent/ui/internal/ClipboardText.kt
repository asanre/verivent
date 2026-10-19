package io.github.asanre.verivent.ui.internal

import androidx.compose.ui.platform.ClipEntry

/** The clip's text, or null when it holds none; common code has no way to read it. */
internal expect fun ClipEntry.plainText(): String?
