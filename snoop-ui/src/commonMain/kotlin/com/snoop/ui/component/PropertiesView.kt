package com.snoop.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.snoop.core.model.KeyValue
import com.snoop.core.model.PlanIssue
import com.snoop.ui.internal.prettyPrintJson
import com.snoop.ui.internal.propertiesToJson

/**
 * Analytics parameters viewer. Pretty = one key-value row per top-level property (nested values
 * stay as compact JSON in the cell), Raw = the whole payload as indented JSON. In Pretty, [issues]
 * mark the properties that break the tracking plan and add the required ones that weren't sent.
 */
@Composable
internal fun PropertiesView(properties: List<KeyValue>, label: String, issues: List<PlanIssue> = emptyList()) {
    var showRaw by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(label, topPadding = 0.dp, bottomPadding = 0.dp)
            SegmentedToggle(
                options = listOf("Pretty", "Raw"),
                selectedIndex = if (showRaw) 1 else 0,
                onSelect = { showRaw = it == 1 },
            )
        }
        if (showRaw) {
            val json = remember(properties) {
                val compact = propertiesToJson(properties)
                prettyPrintJson(compact) ?: compact
            }
            CodePanel {
                JsonSyntaxText(json, Modifier.fillMaxWidth())
            }
        } else {
            SectionCard(modifier = Modifier.padding(top = 8.dp)) {
                val issueFor = issues.associate { it.property to it.message }
                properties.forEach { KeyValueRow(it, valueAlignEnd = true, issue = issueFor[it.name]) }
                val sent = properties.mapTo(HashSet()) { it.name }
                issues.filter { it.property !in sent }.forEach { MissingPropertyRow(it.property) }
            }
        }
    }
}
