package com.snoop.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snoop.core.model.KeyValue
import com.snoop.core.model.NetworkTransaction
import com.snoop.ui.component.CollapsibleSection
import com.snoop.ui.component.KeyValueRow
import com.snoop.ui.component.NetworkBodyView
import com.snoop.ui.component.SectionCard
import com.snoop.ui.component.SectionLabel

@Composable
internal fun Overview(transaction: NetworkTransaction) {
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
        }
    }
}

@Composable
internal fun HeadersAndBody(
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
