package com.snoop.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.snoop.ui.internal.compactPrintJson
import com.snoop.ui.internal.prettyPrintJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Beyond this, colouring the text costs more than it is worth — the panel renders plain. */
private const val HIGHLIGHT_LIMIT = 200_000

private data class JsonBody(val pretty: String, val compact: String)

/**
 * Network body viewer. Pretty = formatted JSON with syntax colors, Raw = the payload compacted to
 * one line (capture may arrive compact or indented — both render the same); the toggle only shows
 * when the payload parses as JSON.
 *
 * Parsing runs off the composition thread — a 250 KB payload would otherwise freeze the frame that
 * opens the entry — so the raw text shows until the formatted one lands.
 */
@Composable
internal fun NetworkBodyView(raw: String) {
    var json by remember(raw) { mutableStateOf<JsonBody?>(null) }
    LaunchedEffect(raw) {
        json = withContext(Dispatchers.Default) {
            prettyPrintJson(raw)?.let { pretty -> JsonBody(pretty, compactPrintJson(raw) ?: raw) }
        }
    }
    var showRaw by remember(raw) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        val body = json
        if (body != null) {
            SegmentedToggle(
                options = listOf("Pretty", "Raw"),
                selectedIndex = if (showRaw) 1 else 0,
                onSelect = { showRaw = it == 1 },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        CodePanel {
            when {
                body == null -> PlainBody(raw)
                showRaw -> PlainBody(body.compact)
                body.pretty.length > HIGHLIGHT_LIMIT -> PlainBody(body.pretty)
                else -> JsonSyntaxText(body.pretty, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PlainBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
    )
}
