package com.snoop.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.Annotation
import com.snoop.core.model.NetworkState
import com.snoop.core.model.NetworkTransaction
import com.snoop.ui.internal.formatClock
import com.snoop.ui.internal.formatDuration
import com.snoop.ui.theme.channelColor
import com.snoop.ui.theme.statusColor

private fun methodColor(method: String): Color = when (method.uppercase()) {
    "GET" -> Color(0xFF2563EB)
    "POST" -> Color(0xFF16A34A)
    "PUT", "PATCH" -> Color(0xFFD97706)
    "DELETE" -> Color(0xFFDC2626)
    else -> Color(0xFF6B7280)
}

@Composable
private fun TimeLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun NetworkRow(transaction: NetworkTransaction, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeLabel(formatClock(transaction.timestamp))
        Pill(transaction.method, methodColor(transaction.method))
        Column(Modifier.weight(1f)) {
            Text(
                text = transaction.path.ifEmpty { "/" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = transaction.host,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val label = when (transaction.state) {
                NetworkState.Pending -> "···"
                NetworkState.Failed -> "ERR"
                NetworkState.Complete -> transaction.status.toString()
            }
            Pill(label, statusColor(transaction))
            transaction.durationMillis?.let {
                Text(
                    text = formatDuration(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun annotationLabel(annotation: Annotation): String = when (annotation) {
    Annotation.Ecommerce -> "ecommerce"
    Annotation.Conversion -> "conversion"
    Annotation.Consent -> "consent"
    Annotation.ScreenView -> "screen"
}

@Composable
internal fun AnalyticsRow(entry: AnalyticsEntry, onClick: () -> Unit) {
    val color = channelColor(entry.channel)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeLabel(formatClock(entry.timestamp))
        Pill(entry.channel, color, filled = true)
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "event",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.annotations.forEach {
                    Text(
                        text = "· ${annotationLabel(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        if (entry.correlatedCallId != null) {
            Text("🔗", style = MaterialTheme.typography.labelMedium)
        }
    }
}
