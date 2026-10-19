package io.github.asanre.verivent.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

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
        modifier = Modifier.size(44.dp),
    ) {
        Icon(InspectorIcons.Copy, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}
