package com.snoop.ui.component

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snoop.core.model.KeyValue
import com.snoop.ui.internal.compactPrintJson
import com.snoop.ui.internal.prettyPrintJson
import com.snoop.ui.internal.propertiesToJson
import com.snoop.ui.theme.TintPair

/** Small rounded label used for status codes, methods, channels and badges. */
@Composable
internal fun Pill(text: String, tint: TintPair, modifier: Modifier = Modifier, mono: Boolean = false) {
    Surface(
        modifier = modifier,
        color = tint.container,
        contentColor = tint.content,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            ),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Rounded surface with a hairline border used to group related detail fields. */
@Composable
internal fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(4.dp)) { content() }
    }
}

@Composable
internal fun SectionLabel(
    text: String,
    fontSize: TextUnit = 11.sp,
    topPadding: Dp = 16.dp,
    bottomPadding: Dp = 6.dp,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding),
    )
}

@Composable
internal fun KeyValueRow(item: KeyValue, valueAlignEnd: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.42f),
            )
            Box(Modifier.weight(0.58f), contentAlignment = if (valueAlignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
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
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = if (valueAlignEnd) TextAlign.End else TextAlign.Start,
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
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth().padding(12.dp)) { content() }
    }
}

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

/**
 * Analytics parameters viewer. Pretty = one key-value row per top-level property (nested values
 * stay as compact JSON in the cell), Raw = the whole payload as indented JSON.
 */
@Composable
internal fun PropertiesView(properties: List<KeyValue>, label: String) {
    var showRaw by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(label, topPadding = 0.dp, bottomPadding = 0.dp)
            SegmentedToggle(
                options = listOf("Pretty", "Raw"),
                selectedIndex = if (showRaw) 1 else 0,
                onSelect = { showRaw = it == 1 },
            )
        }
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
            SectionCard(modifier = Modifier.padding(top = 8.dp)) {
                properties.forEach { KeyValueRow(it, valueAlignEnd = true) }
            }
        }
    }
}
