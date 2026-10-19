package io.github.asanre.verivent.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.asanre.verivent.core.model.NetworkState
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.ui.component.CopyIconButton
import io.github.asanre.verivent.ui.component.DetailScaffold
import io.github.asanre.verivent.ui.component.Pill
import io.github.asanre.verivent.ui.internal.buildCurl
import io.github.asanre.verivent.ui.internal.formatBytes
import io.github.asanre.verivent.ui.internal.formatDuration
import io.github.asanre.verivent.ui.theme.LocalInspectorColors

@Composable
internal fun NetworkDetailScreen(transaction: NetworkTransaction, onBack: () -> Unit) {
    DetailScaffold(
        title = "${transaction.method} ${transaction.path.ifEmpty { "/" }}",
        subtitle = transaction.host,
        onBack = onBack,
        actions = { showMessage ->
            CopyIconButton("Copy as cURL", "Copied as cURL", showMessage) { buildCurl(transaction) }
        },
    ) { modifier, showMessage ->
        Column(modifier) {
            var tab by remember { mutableStateOf(NetworkDetailTab.Overview) }
            val colors = LocalInspectorColors.current
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
                        showMessage = showMessage,
                    )
                    NetworkDetailTab.Response -> HeadersAndBody(
                        headers = transaction.responseHeaders,
                        body = transaction.responseBody?.text,
                        truncated = transaction.responseBody?.truncated == true,
                        headersInitiallyExpanded = false,
                        showMessage = showMessage,
                    )
                }
            }
        }
    }
}
