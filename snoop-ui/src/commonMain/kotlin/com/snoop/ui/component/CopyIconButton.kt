package com.snoop.ui.component

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/**
 * Copy button for the detail screens — cURL, analytics payload, request/response body. [text] is
 * built on tap, not on every recomposition, since two of the three callers serialize to get it.
 * [message] confirms the copy through the screen's snackbar; a silent clipboard write reads as a
 * dropped tap.
 */
@Composable
internal fun CopyIconButton(
    contentDescription: String,
    message: String,
    showMessage: (String) -> Unit,
    text: () -> String,
) {
    val clipboard = LocalClipboardManager.current
    IconButton(
        onClick = {
            clipboard.setText(AnnotatedString(text()))
            showMessage(message)
        },
    ) {
        Icon(SnoopIcons.Copy, contentDescription = contentDescription)
    }
}
