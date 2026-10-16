package com.snoop.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.Annotation
import com.snoop.ui.theme.LocalSnoopColors

@Composable
internal fun AnalyticsRow(
    entry: AnalyticsEntry,
    onClick: () -> Unit,
) {
    val colors = LocalSnoopColors.current
    val tint = colors.channelTint(entry.channel)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = tint.container, shape = RoundedCornerShape(8.dp)) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = SnoopIcons.BarsAnalytics,
                    contentDescription = null,
                    tint = tint.content,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${entry.properties.size} param${if (entry.properties.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PlanBadge(entry)
        Pill(entry.channel, tint)
    }
}

@Composable
private fun PlanBadge(entry: AnalyticsEntry) {
    val colors = LocalSnoopColors.current
    when {
        entry.issues.isNotEmpty() -> Pill(
            "${entry.issues.size} issue${if (entry.issues.size == 1) "" else "s"}",
            colors.error,
        )
        Annotation.NotInPlan in entry.annotations -> Pill(Annotation.NotInPlan.label, colors.neutral)
    }
}
