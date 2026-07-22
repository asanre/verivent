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
import androidx.compose.ui.draw.clip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snoop.core.model.KeyValue
import com.snoop.ui.internal.prettyPrintJson

/** Small rounded label used for status codes, methods, channels and badges. */
@Composable
internal fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Surface(
        modifier = modifier,
        color = if (filled) color else color.copy(alpha = 0.15f),
        contentColor = if (filled) Color.White else color,
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
}

/** Body viewer with a Pretty/Raw toggle; Pretty is offered only when the payload parses as JSON. */
@Composable
internal fun BodyView(raw: String) {
    val pretty = remember(raw) { prettyPrintJson(raw) }
    var showPretty by remember(raw) { mutableStateOf(pretty != null) }
    val shown = if (showPretty && pretty != null) pretty else raw

    Column(Modifier.fillMaxWidth()) {
        if (pretty != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                FilterChip(
                    selected = showPretty,
                    onClick = { showPretty = true },
                    label = { Text("Pretty") },
                    colors = FilterChipDefaults.filterChipColors(),
                )
                FilterChip(
                    selected = !showPretty,
                    onClick = { showPretty = false },
                    label = { Text("Raw") },
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = shown,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
    }
}
