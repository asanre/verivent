package io.github.asanre.verivent.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.asanre.verivent.core.model.KeyValue
import io.github.asanre.verivent.core.model.PlanIssue
import io.github.asanre.verivent.ui.internal.prettyPrintJson
import io.github.asanre.verivent.ui.internal.propertiesToJson
import io.github.asanre.verivent.ui.theme.LocalInspectorColors
import io.github.asanre.verivent.ui.theme.codeTextStyle

/**
 * Analytics parameters viewer. Pretty = one row per top-level property (nested values stay as
 * compact JSON in the cell), Raw = the whole payload as indented JSON. In Pretty, [issues] mark the
 * properties that break the tracking plan and add the required ones that weren't sent. A
 * [collapsible] block starts closed and counts its properties in the label.
 */
@Composable
internal fun PropertiesView(
    properties: List<KeyValue>,
    label: String,
    issues: List<PlanIssue> = emptyList(),
    collapsible: Boolean = false,
) {
    var showRaw by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(label) { mutableStateOf(!collapsible) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (collapsible) Modifier.clickable(interactionSource = null, indication = null) { expanded = !expanded } else Modifier)
                .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (collapsible) {
                val rotation by animateFloatAsState(if (expanded) 90f else 0f)
                Icon(
                    InspectorIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp).rotate(rotation),
                )
            }
            Box(Modifier.weight(1f)) {
                SectionLabel(
                    if (collapsible) "$label · ${properties.size}" else label,
                    topPadding = 0.dp,
                    bottomPadding = 0.dp,
                )
            }
            if (expanded) {
                SegmentedToggle(
                    options = listOf("Pretty", "Raw"),
                    selectedIndex = if (showRaw) 1 else 0,
                    onSelect = { showRaw = it == 1 },
                )
            }
        }
        if (!expanded) return@Column
        if (showRaw) {
            val json = remember(properties) {
                val compact = propertiesToJson(properties)
                prettyPrintJson(compact) ?: compact
            }
            Box(Modifier.padding(horizontal = 16.dp)) {
                CodePanel { JsonSyntaxText(json, Modifier.fillMaxWidth()) }
            }
        } else {
            val issueFor = issues.associate { it.property to it.message }
            val sent = properties.mapTo(HashSet()) { it.name }
            val missing = issues.filter { it.property !in sent }
            SectionCard(Modifier.padding(horizontal = 16.dp), radius = 14.dp, contentPadding = 0.dp) {
                properties.forEachIndexed { index, property ->
                    PropertyRow(property, issueFor[property.name])
                    if (index < properties.lastIndex || missing.isNotEmpty()) {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
                missing.forEach { MissingPropertyRow(it.property) }
            }
        }
    }
}

@Composable
private fun PropertyRow(property: KeyValue, issue: String?) {
    val breaks = LocalInspectorColors.current.planBreaks
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (issue != null) Modifier.background(breaks.card.container) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(0.42f), verticalAlignment = Alignment.CenterVertically) {
                if (issue != null) IssueDot()
                Text(
                    property.name,
                    style = codeTextStyle,
                    color = if (issue != null) breaks.card.content else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.weight(0.58f), contentAlignment = Alignment.CenterEnd) {
                if (property.redacted) {
                    RedactedValue()
                } else {
                    Text(property.value, style = codeTextStyle, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
                }
            }
        }
        issue?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = breaks.card.content,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** A property the tracking plan requires but the event didn't send. */
@Composable
private fun MissingPropertyRow(name: String) {
    val breaks = LocalInspectorColors.current.planBreaks
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .dashedBorder(LocalInspectorColors.current.error.content.copy(alpha = 0.7f), 8.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IssueDot()
        Text(
            name,
            style = codeTextStyle,
            color = breaks.card.content,
            modifier = Modifier.weight(1f),
        )
        Text("missing · required", style = MaterialTheme.typography.bodySmall, color = breaks.card.content)
    }
}

@Composable
private fun IssueDot() {
    Box(
        Modifier.padding(end = 6.dp).size(6.dp).clip(CircleShape)
            .background(LocalInspectorColors.current.error.content),
    )
}
