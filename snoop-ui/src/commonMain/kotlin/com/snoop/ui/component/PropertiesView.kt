package com.snoop.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.snoop.core.model.KeyValue
import com.snoop.ui.internal.prettyPrintJson
import com.snoop.ui.internal.propertiesToJson

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
