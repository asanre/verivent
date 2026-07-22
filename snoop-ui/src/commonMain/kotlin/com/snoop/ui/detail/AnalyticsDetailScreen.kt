package com.snoop.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.Annotation
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import com.snoop.ui.component.DetailScaffold
import com.snoop.ui.component.Pill
import com.snoop.ui.component.PropertiesView
import com.snoop.ui.component.SectionLabel
import com.snoop.ui.internal.formatClock
import com.snoop.ui.internal.propertiesToJson
import com.snoop.ui.theme.LocalSnoopColors

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
    val clipboard = LocalClipboardManager.current
    DetailScaffold(
        title = entry.name,
        subtitle = "event · ${entry.channel}",
        onBack = onBack,
        actions = { showMessage ->
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(propertiesToJson(entry.properties)))
                showMessage("Copied payload")
            }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy payload")
            }
        },
    ) { modifier, _ ->
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val colors = LocalSnoopColors.current
            val tint = colors.channelTint(entry.channel)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                Pill(entry.channel, tint)
                entry.annotations.forEach { Pill(annotationLabel(it), tint) }
                Pill(formatClock(entry.timestamp), colors.neutral)
            }

            val call = entry.correlatedCallId?.let { id ->
                allEntries.filterIsInstance<NetworkTransaction>().firstOrNull { it.id == id }
            }
            if (call != null) {
                SectionLabel("Sent over")
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = colors.accent.container,
                    contentColor = colors.accent.content,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenCall(call.id) },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "🔗 ${call.method} ${call.path}  →  ${call.status ?: "…"}",
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

            SectionLabel("Parameters")
            if (entry.properties.isEmpty()) {
                Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                PropertiesView(entry.properties)
            }
        }
    }
}
