package com.snoop.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.snoop.ui.internal.compactPrintJson
import com.snoop.ui.internal.prettyPrintJson

/**
 * Network body viewer. Pretty = formatted JSON with syntax colors, Raw = the payload compacted to
 * one line (capture may arrive compact or indented — both render the same); the toggle only shows
 * when the payload parses as JSON.
 */
@Composable
internal fun NetworkBodyView(raw: String) {
    val pretty = remember(raw) { prettyPrintJson(raw) }
    var showRaw by remember(raw) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        if (pretty != null) {
            SegmentedToggle(
                options = listOf("Pretty", "Raw"),
                selectedIndex = if (showRaw) 1 else 0,
                onSelect = { showRaw = it == 1 },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        CodePanel {
            if (pretty != null && !showRaw) {
                JsonSyntaxText(pretty, Modifier.fillMaxWidth())
            } else {
                val compact = remember(raw) { compactPrintJson(raw) }
                Text(
                    text = compact ?: raw,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
