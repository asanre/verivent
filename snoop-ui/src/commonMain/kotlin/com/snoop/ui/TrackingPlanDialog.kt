package com.snoop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.snoop.core.SnoopCore
import com.snoop.ui.theme.LocalSnoopColors

/**
 * Paste-in loader for the tracking plan. Pasting is the one input every device surface has,
 * BrowserStack's remote session included, so there is no file picker to maintain per platform.
 */
@Composable
internal fun TrackingPlanDialog(onDismiss: () -> Unit) {
    val version = SnoopCore.trackingPlan.collectAsState().value?.version
    var json by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tracking plan") },
        text = {
            Column {
                Text(
                    version?.let { "Loaded: version $it" } ?: "No tracking plan loaded: events are not validated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = json,
                    onValueChange = {
                        json = it
                        error = null
                    },
                    placeholder = { Text("Paste the tracking plan JSON") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp).padding(top = 12.dp),
                )
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalSnoopColors.current.error.content,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = json.isNotBlank(),
                onClick = {
                    try {
                        SnoopCore.loadTrackingPlan(json)
                        onDismiss()
                    } catch (invalid: IllegalArgumentException) {
                        error = invalid.message
                    }
                },
            ) { Text("Load") }
        },
        dismissButton = {
            Row {
                if (version != null) {
                    TextButton(onClick = { SnoopCore.clearTrackingPlan() }) { Text("Clear plan") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
