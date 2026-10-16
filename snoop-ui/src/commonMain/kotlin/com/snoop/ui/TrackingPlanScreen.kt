package com.snoop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snoop.core.SnoopCore
import com.snoop.core.model.PlanSummary
import com.snoop.core.model.SnoopEntry
import com.snoop.ui.component.DetailScaffold
import com.snoop.ui.component.SectionLabel
import com.snoop.ui.component.SnoopIcons
import com.snoop.ui.internal.EventCoverage
import com.snoop.ui.internal.PlanVerdict
import com.snoop.ui.internal.plainText
import com.snoop.ui.internal.planCoverage
import com.snoop.ui.theme.LocalSnoopColors
import com.snoop.ui.theme.TintPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the tracking plan is loaded, replaced or cleared, and where the session is held against it. */
@Composable
internal fun TrackingPlanScreen(entries: List<SnoopEntry>, onBack: () -> Unit) {
    val plan by SnoopCore.trackingPlan.collectAsState()
    var replacing by remember { mutableStateOf(false) }
    DetailScaffold(
        title = "Tracking plan",
        subtitle = null,
        onBack = onBack,
    ) { modifier, _ ->
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val loaded = plan
            if (loaded == null || replacing) {
                PlanLoader(current = loaded, onLoaded = { replacing = false }, onCancel = { replacing = false })
            } else {
                LoadedPlan(loaded, entries, onReplace = { replacing = true })
            }
        }
    }
}

@Composable
private fun PlanLoader(current: PlanSummary?, onLoaded: () -> Unit, onCancel: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var json by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Parsed and stored off the main thread: a plan can be large, and it is written to disk.
    fun load(text: String) {
        scope.launch {
            val failure = withContext(Dispatchers.Default) {
                runCatching { SnoopCore.loadTrackingPlan(text) }.exceptionOrNull()
            }
            when (failure) {
                null -> {
                    json = ""
                    error = null
                    onLoaded()
                }
                is IllegalArgumentException -> {
                    json = text
                    error = failure.message.orEmpty().removePrefix("tracking plan: ").replaceFirstChar { it.uppercase() } +
                        (current?.let { ". Nothing changed: ${it.version} is still loaded." } ?: ".")
                }
                else -> throw failure
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    if (current == null) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = scheme.surface,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                shape = RoundedCornerShape(18.dp),
            ) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Icon(SnoopIcons.Plan, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Text("Check events against the plan", style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), fontWeight = FontWeight.SemiBold)
            Text(
                "Load the analytics team's tracking plan and every event it defines, captured from then on, is " +
                    "checked for missing or wrong parameters.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        SectionLabel("Replace version ${current.version}", topPadding = 0.dp, bottomPadding = 0.dp)
    }

    Button(
        onClick = {
            scope.launch {
                val pasted = clipboard.getClipEntry()?.plainText()
                if (pasted.isNullOrBlank()) error = "The clipboard has no text to paste." else load(pasted)
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = scheme.onSurface, contentColor = scheme.surface),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Icon(SnoopIcons.Clipboard, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Paste from clipboard", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = scheme.outlineVariant)
        Text("or paste it here", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f), color = scheme.outlineVariant)
    }
    OutlinedTextField(
        value = json,
        onValueChange = {
            json = it
            error = null
        },
        placeholder = {
            Text(
                "{ \"version\": \"…\", \"events\": [ … ] }",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            )
        },
        isError = error != null,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = scheme.surface,
            focusedContainerColor = scheme.surface,
            errorContainerColor = scheme.surface,
            unfocusedBorderColor = scheme.outline,
        ),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
    )
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalSnoopColors.current.error.content) }
    if (json.isNotBlank() || current != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (current != null) TextButton(onClick = onCancel) { Text("Cancel", color = scheme.onSurface) }
            Button(
                onClick = { load(json) },
                enabled = json.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.onSurface, contentColor = scheme.surface),
            ) { Text("Load plan", fontWeight = FontWeight.SemiBold) }
        }
    }

    Surface(
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Testing on BrowserStack?", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), fontWeight = FontWeight.SemiBold)
            Text(
                "Copy the plan's JSON on your computer, tap the field above and press ⌘V. The remote device receives what you paste.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = scheme.onSurfaceVariant,
            )
        }
    }
    Text(
        if (SnoopCore.storageDir != null) "The plan stays on this device until you clear it." else "The plan lasts until the app restarts.",
        style = MaterialTheme.typography.labelMedium,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
    )
}

@Composable
private fun LoadedPlan(plan: PlanSummary, entries: List<SnoopEntry>, onReplace: () -> Unit) {
    val colors = LocalSnoopColors.current
    val coverage = remember(plan, entries) { planCoverage(entries, plan) }
    val byVerdict = coverage.groupBy { it.verdict }
    var showAllUnseen by remember { mutableStateOf(false) }

    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Version ${plan.version}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${plan.events.size} events", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = onReplace,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, scheme.outline),
            ) { Text("Replace", color = scheme.onSurface, fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = { SnoopCore.clearTrackingPlan() },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, colors.error.container),
            ) { Text("Clear", color = colors.error.content, fontWeight = FontWeight.SemiBold) }
        }
    }

    SectionLabel("This session against the plan", topPadding = 8.dp, bottomPadding = 0.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CoverageCount(byVerdict[PlanVerdict.Breaks].orEmpty().size, "break it", colors.error, Modifier.weight(1f))
        CoverageCount(byVerdict[PlanVerdict.Matches].orEmpty().size, "match it", colors.success, Modifier.weight(1f))
        CoverageCount(
            byVerdict[PlanVerdict.NotSeen].orEmpty().size,
            "not seen yet",
            TintPair(scheme.outlineVariant, scheme.onSurfaceVariant),
            Modifier.weight(1f),
        )
    }

    Surface(
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            byVerdict[PlanVerdict.Breaks]?.let { CoverageGroup("Breaks the plan", colors.error, it) }
            byVerdict[PlanVerdict.Matches]?.let { CoverageGroup("Matches the plan", colors.success, it) }
            byVerdict[PlanVerdict.NotSeen]?.let { unseen ->
                val shown = if (showAllUnseen) unseen else unseen.take(UNSEEN_PREVIEW)
                CoverageGroup("Not seen yet", colors.neutral, shown)
                if (shown.size < unseen.size) {
                    TextButton(onClick = { showAllUnseen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Show ${unseen.size - shown.size} more", color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    Text(
        "Events the plan doesn't define aren't checked.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val UNSEEN_PREVIEW = 3

@Composable
private fun CoverageCount(count: Int, label: String, tint: TintPair, modifier: Modifier) {
    Surface(
        color = tint.container.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, tint.container),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = tint.content)
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint.content)
        }
    }
}

@Composable
private fun CoverageGroup(title: String, tint: TintPair, events: List<EventCoverage>) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = tint.content,
        modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 6.dp),
    )
    events.forEach { coverage ->
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val dot = Modifier.size(8.dp).clip(CircleShape)
            Box(
                if (coverage.verdict == PlanVerdict.NotSeen) {
                    dot.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                } else {
                    dot.background(tint.content)
                },
            )
            Text(
                coverage.event.name,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                modifier = Modifier.weight(1f),
            )
            Text(
                when (coverage.verdict) {
                    PlanVerdict.Breaks -> "${coverage.event.channel} · ${coverage.breaking} of ${coverage.captured}"
                    PlanVerdict.Matches -> "${coverage.event.channel} · ${coverage.captured} seen"
                    PlanVerdict.NotSeen -> coverage.event.channel
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
