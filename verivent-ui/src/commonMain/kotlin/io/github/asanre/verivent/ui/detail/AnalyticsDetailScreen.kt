package io.github.asanre.verivent.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.Annotation
import io.github.asanre.verivent.core.model.PlanIssue
import io.github.asanre.verivent.core.model.PlanSummary
import io.github.asanre.verivent.ui.component.CopyIconButton
import io.github.asanre.verivent.ui.component.DetailScaffold
import io.github.asanre.verivent.ui.component.Pill
import io.github.asanre.verivent.ui.component.PropertiesView
import io.github.asanre.verivent.ui.component.SectionLabel
import io.github.asanre.verivent.ui.component.InspectorIcons
import io.github.asanre.verivent.ui.component.dashedBorder
import io.github.asanre.verivent.ui.internal.checkedBy
import io.github.asanre.verivent.ui.internal.formatClock
import io.github.asanre.verivent.ui.internal.problemCount
import io.github.asanre.verivent.ui.internal.propertiesToJson
import io.github.asanre.verivent.ui.theme.LocalInspectorColors

@Composable
internal fun AnalyticsDetailScreen(
    entry: AnalyticsEntry,
    plan: PlanSummary?,
    onBack: () -> Unit,
) {
    val issues = if (entry.checkedBy(plan)) entry.issues else emptyList()
    DetailScaffold(
        title = entry.name,
        subtitle = "event · ${entry.channel}",
        onBack = onBack,
        actions = { showMessage ->
            CopyIconButton("Copy payload", "Copied payload", showMessage) { propertiesToJson(entry.properties) }
        },
    ) { modifier, _ ->
        Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            val colors = LocalInspectorColors.current
            val tint = colors.channelTint(entry.channel)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
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

            if (plan != null && entry.checkedBy(plan)) PlanVerdictCard(plan.version, issues)

            if (entry.properties.isEmpty() && issues.isEmpty()) {
                SectionLabel("Parameters", topPadding = 22.dp)
                Text(
                    "None",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                PropertiesView(entry.properties, label = "Parameters", issues = issues)
            }
            if (entry.checkedBy(plan)) CheckedAtCaptureNote()

            entry.sections.forEach { (title, section) ->
                key(title) { PropertiesView(section, label = title, collapsible = true) }
            }
        }
    }
}

@Composable
private fun CheckedAtCaptureNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .dashedBorder(MaterialTheme.colorScheme.outline, 12.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(InspectorIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(
            "Checked when it was captured. Clearing or replacing the plan hides this verdict, and events captured before a plan is loaded aren't checked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlanVerdictCard(planVersion: String, issues: List<PlanIssue>) {
    val verdict = if (issues.isEmpty()) LocalInspectorColors.current.planMatches else LocalInspectorColors.current.planBreaks
    Surface(
        color = verdict.card.container,
        border = BorderStroke(1.dp, verdict.border),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = if (issues.isEmpty()) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (issues.isEmpty()) InspectorIcons.MatchesCircle else InspectorIcons.BreaksCircle,
                    contentDescription = null,
                    tint = verdict.pill.content,
                    modifier = Modifier.size(22.dp),
                )
                Column {
                    Text(
                        if (issues.isEmpty()) "Matches tracking plan $planVersion" else "Breaks tracking plan $planVersion",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = verdict.card.content,
                    )
                    Text(
                        if (issues.isEmpty()) "Every parameter is what the plan expects" else "${problemCount(issues.size)} in its parameters",
                        style = MaterialTheme.typography.bodySmall,
                        color = verdict.muted,
                    )
                }
            }
            if (issues.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    issues.forEach { issue ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, verdict.border),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    issue.property,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = verdict.card.content,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Text(
                                issue.message,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
