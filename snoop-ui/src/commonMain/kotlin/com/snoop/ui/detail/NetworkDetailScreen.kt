package com.snoop.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkState
import com.snoop.core.model.NetworkTransaction
import com.snoop.ui.component.CollapsibleSection
import com.snoop.ui.component.DetailScaffold
import com.snoop.ui.component.KeyValueRow
import com.snoop.ui.component.NetworkBodyView
import com.snoop.ui.component.Pill
import com.snoop.ui.component.SectionCard
import com.snoop.ui.component.SectionLabel
import com.snoop.ui.component.SnoopIcons
import com.snoop.ui.internal.buildCurl
import com.snoop.ui.internal.formatBytes
import com.snoop.ui.internal.formatDuration
import com.snoop.ui.theme.LocalSnoopColors

private enum class NetworkDetailTab { Overview, Request, Response }

@Composable
internal fun NetworkDetailScreen(transaction: NetworkTransaction, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    DetailScaffold(
        title = "${transaction.method} ${transaction.path.ifEmpty { "/" }}",
        subtitle = transaction.host,
        onBack = onBack,
        actions = { showMessage ->
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(buildCurl(transaction)))
                showMessage("Copied as cURL")
            }) {
                Icon(SnoopIcons.Copy, contentDescription = "Copy as cURL")
            }
        },
    ) { modifier, _ ->
        Column(modifier) {
            var tab by remember { mutableStateOf(NetworkDetailTab.Overview) }
            val colors = LocalSnoopColors.current
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val statusLabel = when (transaction.state) {
                    NetworkState.Pending -> "pending"
                    NetworkState.Failed -> "failed"
                    NetworkState.Complete -> transaction.status.toString()
                }
                Pill(statusLabel, colors.statusTint(transaction), mono = true)
                transaction.durationMillis?.let { Pill(formatDuration(it), colors.neutral, mono = true) }
                transaction.responseBody?.let { Pill(formatBytes(it.byteCount), colors.neutral, mono = true) }
                Pill("ktor", colors.neutral, mono = true)
            }
            TextTabs(selected = tab, onSelect = { tab = it })
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                when (tab) {
                    NetworkDetailTab.Overview -> Overview(transaction)
                    NetworkDetailTab.Request -> HeadersAndBody(
                        headers = transaction.requestHeaders,
                        body = transaction.requestBody?.text,
                        truncated = transaction.requestBody?.truncated == true,
                        headersInitiallyExpanded = true,
                    )
                    NetworkDetailTab.Response -> HeadersAndBody(
                        headers = transaction.responseHeaders,
                        body = transaction.responseBody?.text,
                        truncated = transaction.responseBody?.truncated == true,
                        headersInitiallyExpanded = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextTabs(selected: NetworkDetailTab, onSelect: (NetworkDetailTab) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            NetworkDetailTab.entries.forEach { tab ->
                val active = tab == selected
                Column(Modifier.width(IntrinsicSize.Max).clickable { onSelect(tab) }) {
                    Text(
                        text = tab.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Overview(transaction: NetworkTransaction) {
    SectionCard {
        Column(Modifier.padding(12.dp)) {
            SectionLabel("URL", fontSize = 10.sp, topPadding = 0.dp)
            Text(transaction.url, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))

            transaction.protocol?.let {
                SectionLabel("Protocol", fontSize = 10.sp)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            transaction.error?.let {
                SectionLabel("Error", fontSize = 10.sp)
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            if (transaction.correlatedEventIds.isNotEmpty()) {
                SectionLabel("Analytics", fontSize = 10.sp)
                Text(
                    "${transaction.correlatedEventIds.size} analytics event(s) sent over this call.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun HeadersAndBody(
    headers: List<KeyValue>,
    body: String?,
    truncated: Boolean,
    headersInitiallyExpanded: Boolean,
) {
    SectionCard {
        Column(Modifier.padding(12.dp)) {
            CollapsibleSection("Headers (${headers.size})", initiallyExpanded = headersInitiallyExpanded) {
                if (headers.isEmpty()) {
                    Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        headers.forEach { KeyValueRow(it) }
                    }
                }
            }
        }
    }
    SectionCard(modifier = Modifier.padding(top = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            SectionLabel("Body", fontSize = 10.sp, topPadding = 0.dp)
            if (body.isNullOrEmpty()) {
                Text("Empty", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                NetworkBodyView(body)
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
    }
}
