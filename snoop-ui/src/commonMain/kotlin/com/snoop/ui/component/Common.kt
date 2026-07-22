package com.snoop.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snoop.core.model.KeyValue
import com.snoop.ui.internal.prettyPrintJson
import com.snoop.ui.internal.propertiesToJson
import com.snoop.ui.theme.TintPair

/** Small rounded label used for status codes, methods, channels and badges. */
@Composable
internal fun Pill(text: String, tint: TintPair, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = tint.container,
        contentColor = tint.content,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
internal fun KeyValueRow(item: KeyValue) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.42f),
            )
            Box(Modifier.weight(0.58f)) {
                if (item.redacted) {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .width(64.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onSurface),
                    )
                } else {
                    Text(
                        text = item.value,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun CodePanel(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth().padding(12.dp)) { content() }
    }
}

/**
 * Network body viewer. Pretty = formatted JSON with syntax colors, Raw = the same formatted text
 * without colors (capture may arrive compact or indented — both render the same); the toggle only
 * shows when the payload parses as JSON.
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
                Text(
                    text = pretty ?: raw,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Analytics parameters viewer. Pretty = one key-value row per top-level property (nested values
 * stay as compact JSON in the cell), Raw = the whole payload as indented JSON.
 */
@Composable
internal fun PropertiesView(properties: List<KeyValue>) {
    var showRaw by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        SegmentedToggle(
            options = listOf("Pretty", "Raw"),
            selectedIndex = if (showRaw) 1 else 0,
            onSelect = { showRaw = it == 1 },
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (showRaw) {
            val json = remember(properties) {
                val compact = propertiesToJson(properties)
                prettyPrintJson(compact) ?: compact
            }
            CodePanel {
                Text(
                    text = json,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                properties.forEach { KeyValueRow(it) }
            }
        }
    }
}
