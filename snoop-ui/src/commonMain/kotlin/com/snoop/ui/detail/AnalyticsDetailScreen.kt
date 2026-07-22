package com.snoop.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.Annotation
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import com.snoop.ui.component.BodyView
import com.snoop.ui.component.DetailScaffold
import com.snoop.ui.component.KeyValueRow
import com.snoop.ui.component.Pill
import com.snoop.ui.component.SectionLabel
import com.snoop.ui.internal.propertiesToJson
import com.snoop.ui.theme.channelColor

private fun annotationLabel(annotation: Annotation): String = when (annotation) {
    Annotation.Ecommerce -> "ecommerce"
    Annotation.Conversion -> "conversion"
    Annotation.Consent -> "consent"
    Annotation.ScreenView -> "screen"
}

@Composable
internal fun AnalyticsDetailScreen(
    entry: AnalyticsEntry,
    allEntries: List<SnoopEntry>,
    onOpenCall: (String) -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = entry.name, subtitle = "event · ${entry.channel}", onBack = onBack) { modifier ->
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val color = channelColor(entry.channel)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(entry.channel, color, filled = true)
                entry.annotations.forEach { Pill(annotationLabel(it), color) }
            }

            val call = entry.correlatedCallId?.let { id ->
                allEntries.filterIsInstance<NetworkTransaction>().firstOrNull { it.id == id }
            }
            if (call != null) {
                SectionLabel("Sent over")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenCall(call.id) },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "${call.method} ${call.path}  →  ${call.status ?: "…"}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Tap to open the request",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionLabel("Properties")
            if (entry.properties.isEmpty()) {
                Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                entry.properties.forEach { KeyValueRow(it) }

                SectionLabel("Payload")
                BodyView(propertiesToJson(entry.properties))
            }
        }
    }
}
