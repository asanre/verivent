package com.snoop.ui.detail

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.Annotation
import com.snoop.core.model.PlanIssue
import com.snoop.ui.component.CopyIconButton
import com.snoop.ui.component.DetailScaffold
import com.snoop.ui.component.Pill
import com.snoop.ui.component.PropertiesView
import com.snoop.ui.component.SectionLabel
import com.snoop.ui.component.SnoopIcons
import com.snoop.ui.internal.formatClock
import com.snoop.ui.internal.problemCount
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
                    val annotationTint = when (it) {
                        Annotation.Ecommerce -> colors.ecom
                        else -> tint
                    }
                    Pill(it.label, annotationTint)
                }
                Pill(formatClock(entry.timestamp), colors.neutral, mono = true)
            }

            entry.planVersion?.let { version -> PlanVerdictCard(version, entry.issues) }

            Column(Modifier.padding(top = 16.dp)) {
                if (entry.properties.isEmpty()) {
                    SectionLabel("Parameters")
                    Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    PropertiesView(entry.properties, label = "Parameters", issues = entry.issues)
                }
                if (entry.planVersion != null) {
                    Text(
                        "Checked when it was captured. Loading another plan only changes the verdict of events captured after it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            entry.sections.forEach { (title, section) ->
                key(title) {
                    Column(Modifier.padding(top = 32.dp)) {
                        PropertiesView(section, label = title)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanVerdictCard(planVersion: String, issues: List<PlanIssue>) {
    val colors = LocalSnoopColors.current
    val tint = if (issues.isEmpty()) colors.success else colors.error
    Surface(
        color = tint.container.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, tint.container),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (issues.isEmpty()) SnoopIcons.Check else SnoopIcons.Close,
                    contentDescription = null,
                    tint = tint.content,
                    modifier = Modifier.size(18.dp),
                )
                Column {
                    Text(
                        if (issues.isEmpty()) "Matches tracking plan $planVersion" else "Breaks tracking plan $planVersion",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = tint.content,
                    )
                    Text(
                        if (issues.isEmpty()) "Every parameter is what the plan expects" else "${problemCount(issues.size)} in its parameters",
                        style = MaterialTheme.typography.labelMedium,
                        color = tint.content,
                    )
                }
            }
            issues.forEach { issue ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, tint.container),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            issue.property,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                            color = tint.content,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(issue.message, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
