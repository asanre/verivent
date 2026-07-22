package com.snoop.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.snoop.core.model.NetworkState
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import com.snoop.ui.component.Pill
import com.snoop.ui.theme.channelColor

/** Aggregated view: top analytics events (à la Firebase DebugView) and network status counts. */
@Composable
internal fun SummaryContent(entries: List<SnoopEntry>) {
    val events = entries.filterIsInstance<AnalyticsEntry>()
    val calls = entries.filterIsInstance<NetworkTransaction>()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card("Network") {
            if (calls.isEmpty()) {
                Empty()
            } else {
                StatRow("Total", calls.size.toString())
                val byState = calls.groupingBy { it.state }.eachCount()
                StatRow("Completed", (byState[NetworkState.Complete] ?: 0).toString())
                StatRow("Failed", (byState[NetworkState.Failed] ?: 0).toString())
                StatRow("Pending", (byState[NetworkState.Pending] ?: 0).toString())
            }
        }

        Card("Top events") {
            if (events.isEmpty()) {
                Empty()
            } else {
                events.groupingBy { it.name }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .take(10)
                    .forEach { (name, count) ->
                        val channel = events.first { it.name == name }.channel
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Pill(channel, channelColor(channel), filled = true)
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(count.toString(), fontWeight = FontWeight.Bold)
                        }
                    }
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Empty() {
    Text("No data yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
