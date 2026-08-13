package com.snoop.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.Annotation
import com.snoop.ui.component.CopyIconButton
import com.snoop.ui.component.DetailScaffold
import com.snoop.ui.component.Pill
import com.snoop.ui.component.PropertiesView
import com.snoop.ui.component.SectionLabel
import com.snoop.ui.internal.formatClock
import com.snoop.ui.internal.propertiesToJson
import com.snoop.ui.theme.LocalSnoopColors

@Composable
internal fun AnalyticsDetailScreen(
    entry: AnalyticsEntry,
    onBack: () -> Unit,
) {
    DetailScaffold(
        title = entry.name,
        subtitle = "event · ${entry.channel}",
        onBack = onBack,
        actions = { showMessage ->
            CopyIconButton("Copy payload", "Copied payload", showMessage) { propertiesToJson(entry.properties) }
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
                    Pill(it.label, annotationTint)
                }
                Pill(formatClock(entry.timestamp), colors.neutral, mono = true)
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
