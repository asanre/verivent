package com.snoop.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.snoop.ui.component.SnoopIcons
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
                Icon(SnoopIcons.Copy, contentDescription = "Copy payload")
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
                entry.annotations.forEach {
                    val annotationTint = if (it == Annotation.Ecommerce) colors.ecom else tint
                    Pill(annotationLabel(it), annotationTint)
                }
                Pill(formatClock(entry.timestamp), colors.neutral, mono = true)
            }

            val call = entry.correlatedCallId?.let { id ->
                allEntries.filterIsInstance<NetworkTransaction>().firstOrNull { it.id == id }
            }
            if (call != null) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clickable { onOpenCall(call.id) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = SnoopIcons.Link,
                            contentDescription = null,
                            tint = colors.accent.content,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "${call.method} ${call.path}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Pill(call.status?.toString() ?: "…", colors.statusTint(call), mono = true)
                        Icon(
                            imageVector = SnoopIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Column(Modifier.padding(top = 16.dp)) {
                if (entry.properties.isEmpty()) {
                    SectionLabel("Parameters")
                    Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    PropertiesView(entry.properties, label = "Parameters")
                }
            }
        }
    }
}
