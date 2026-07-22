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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkState
import com.snoop.core.model.NetworkTransaction
import com.snoop.ui.component.BodyView
import com.snoop.ui.component.DetailScaffold
import com.snoop.ui.component.KeyValueRow
import com.snoop.ui.component.Pill
import com.snoop.ui.component.SectionLabel
import com.snoop.ui.internal.formatBytes
import com.snoop.ui.internal.formatDuration
import com.snoop.ui.theme.statusColor

private enum class NetworkDetailTab { Overview, Request, Response }

@Composable
internal fun NetworkDetailScreen(transaction: NetworkTransaction, onBack: () -> Unit) {
    DetailScaffold(
        title = "${transaction.method} ${transaction.path.ifEmpty { "/" }}",
        subtitle = transaction.host,
        onBack = onBack,
    ) { modifier ->
        Column(modifier) {
            var tab by remember { mutableStateOf(NetworkDetailTab.Overview) }
            TabRow(selectedTabIndex = tab.ordinal) {
                NetworkDetailTab.entries.forEach { option ->
                    Tab(
                        selected = tab == option,
                        onClick = { tab = option },
                        text = { Text(option.name) },
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                when (tab) {
                    NetworkDetailTab.Overview -> Overview(transaction)
                    NetworkDetailTab.Request -> HeadersAndBody(transaction.requestContentType, transaction.requestHeaders, transaction.requestBody?.text, transaction.requestBody?.truncated == true)
                    NetworkDetailTab.Response -> HeadersAndBody(transaction.responseContentType, transaction.responseHeaders, transaction.responseBody?.text, transaction.responseBody?.truncated == true)
                }
            }
        }
    }
}

@Composable
private fun Overview(transaction: NetworkTransaction) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val statusLabel = when (transaction.state) {
            NetworkState.Pending -> "pending"
            NetworkState.Failed -> "failed"
            NetworkState.Complete -> transaction.status.toString()
        }
        Pill(statusLabel, statusColor(transaction), filled = true)
        transaction.durationMillis?.let { Pill(formatDuration(it), MaterialTheme.colorScheme.primary) }
        transaction.responseBody?.let { Pill(formatBytes(it.byteCount), MaterialTheme.colorScheme.tertiary) }
        Pill(transaction.method, MaterialTheme.colorScheme.secondary)
    }

    SectionLabel("URL")
    Text(transaction.url, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))

    transaction.protocol?.let {
        SectionLabel("Protocol")
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }

    transaction.error?.let {
        SectionLabel("Error")
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }

    if (transaction.correlatedEventIds.isNotEmpty()) {
        SectionLabel("Analytics")
        Text(
            "${transaction.correlatedEventIds.size} analytics event(s) sent over this call.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun HeadersAndBody(
    contentType: String?,
    headers: List<KeyValue>,
    body: String?,
    truncated: Boolean,
) {
    SectionLabel("Headers")
    if (headers.isEmpty()) {
        Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        headers.forEach { KeyValueRow(it) }
    }

    SectionLabel("Body")
    contentType?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
    }
    if (body.isNullOrEmpty()) {
        Text("Empty", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        BodyView(body)
        if (truncated) {
            Text(
                "Body truncated to the configured limit.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
