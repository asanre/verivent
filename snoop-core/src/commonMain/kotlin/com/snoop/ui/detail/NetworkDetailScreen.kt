package com.snoop.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.snoop.core.model.NetworkState
import com.snoop.core.model.NetworkTransaction
import com.snoop.ui.component.DetailScaffold
import com.snoop.ui.component.Pill
import com.snoop.ui.component.SnoopIcons
import com.snoop.ui.internal.buildCurl
import com.snoop.ui.internal.formatBytes
import com.snoop.ui.internal.formatDuration
import com.snoop.ui.theme.LocalSnoopColors

internal enum class NetworkDetailTab { Overview, Request, Response }

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
