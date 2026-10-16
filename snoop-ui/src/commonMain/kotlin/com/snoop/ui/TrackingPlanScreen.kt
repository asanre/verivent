package com.snoop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snoop.core.SnoopCore
import com.snoop.core.model.PlanSummary
import com.snoop.core.model.SnoopEntry
import com.snoop.ui.component.DetailScaffold
import com.snoop.ui.component.SectionCard
import com.snoop.ui.component.SectionLabel
import com.snoop.ui.component.SnoopIcons
import com.snoop.ui.internal.EventCoverage
import com.snoop.ui.internal.PlanVerdict
import com.snoop.ui.internal.plainText
import com.snoop.ui.internal.planCoverage
import com.snoop.ui.theme.LocalSnoopColors
import com.snoop.ui.theme.VerdictColors
import com.snoop.ui.theme.codeTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the tracking plan is loaded, replaced or cleared, and where the session is held against it. */
@Composable
internal fun TrackingPlanScreen(entries: List<SnoopEntry>, onBack: () -> Unit) {
    val plan by SnoopCore.trackingPlan.collectAsState()
    var replacing by remember { mutableStateOf(false) }
    DetailScaffold(title = "Tracking plan", subtitle = null, onBack = onBack) { modifier, _ ->
        val loaded = plan
        if (loaded == null || replacing) {
            PlanLoader(modifier, current = loaded, onLoaded = { replacing = false }, onCancel = { replacing = false })
        } else {
            Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                LoadedPlan(loaded, entries, onReplace = { replacing = true })
            }
        }
    }
}

@Composable
private fun PlanLoader(modifier: Modifier, current: PlanSummary?, onLoaded: () -> Unit, onCancel: () -> Unit) {
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
                    error = failure.message.orEmpty().replaceFirstChar { it.uppercase() } +
                        (current?.let { ". Nothing changed: ${it.version} is still loaded." } ?: ".")
                }
                else -> throw failure
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val inverted = ButtonDefaults.buttonColors(containerColor = scheme.onSurface, contentColor = scheme.surface)
    // The footer stays at the bottom, as on the design; only what is above it scrolls.
    Column(modifier) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (current == null) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 40.dp, bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(color = scheme.surface, border = BorderStroke(1.dp, scheme.outlineVariant), shape = RoundedCornerShape(18.dp)) {
                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            Icon(SnoopIcons.PlanBoard, contentDescription = null, modifier = Modifier.size(32.dp))
                        }
                    }
                    Text(
                        "Check events against the plan",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Load the analytics team's tracking plan and every event it defines, captured from then on, is " +
                            "checked for missing or wrong parameters.",
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SectionLabel("Replace version ${current.version}", topPadding = 8.dp, bottomPadding = 12.dp)
                }
            }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val pasted = clipboard.getClipEntry()?.plainText()
                            if (pasted.isNullOrBlank()) error = "The clipboard has no text to paste." else load(pasted)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = inverted,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(SnoopIcons.Clipboard, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        "Paste from clipboard",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(Modifier.weight(1f), color = scheme.outlineVariant)
                    Text("or paste it here", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.weight(1f), color = scheme.outlineVariant)
                }
                PlanJsonField(json, isError = error != null) {
                    json = it
                    error = null
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalSnoopColors.current.planBreaks.card.content) }
                if (json.isNotBlank() || current != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        if (current != null) TextButton(onClick = onCancel) { Text("Cancel", color = scheme.onSurface) }
                        Button(
                            onClick = { load(json) },
                            enabled = json.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = inverted,
                        ) { Text("Load plan", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }

            SectionCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp), radius = 14.dp, contentPadding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Testing on BrowserStack?", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), fontWeight = FontWeight.SemiBold)
                    Text(
                        "Copy the plan's JSON on your computer, tap the field above and press ⌘V. The remote device receives what you paste.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            if (SnoopCore.storageDir != null) "The plan stays on this device until you clear it." else "The plan lasts until the app restarts.",
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun PlanJsonField(json: String, isError: Boolean, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = json,
        onValueChange = onChange,
        placeholder = { Text("{ \"version\": \"…\", \"events\": [ … ] }", style = codeTextStyle) },
        isError = isError,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = scheme.surface,
            focusedContainerColor = scheme.surface,
            errorContainerColor = scheme.surface,
            unfocusedBorderColor = scheme.outline,
            focusedBorderColor = scheme.onSurface,
        ),
        textStyle = codeTextStyle,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
    )
}

@Composable
private fun LoadedPlan(plan: PlanSummary, entries: List<SnoopEntry>, onReplace: () -> Unit) {
    val colors = LocalSnoopColors.current
    val scheme = MaterialTheme.colorScheme
    val coverage = remember(plan, entries) { planCoverage(entries, plan) }
    val byVerdict = coverage.groupBy { it.verdict }
    var showAllUnseen by remember { mutableStateOf(false) }

    SectionCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), radius = 16.dp, contentPadding = 0.dp) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Version ${plan.version}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("${plan.events.size} events", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            SmallOutlinedButton("Replace", scheme.onSurface, scheme.outline, onReplace)
            SmallOutlinedButton("Clear", colors.planBreaks.pill.content, colors.planBreaks.border) { SnoopCore.clearTrackingPlan() }
        }
    }

    Box(Modifier.padding(horizontal = 16.dp)) {
        SectionLabel("This session against the plan", topPadding = 20.dp, bottomPadding = 8.dp)
    }
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CoverageCount(byVerdict[PlanVerdict.Breaks].orEmpty().size, "break it", colors.planBreaks, Modifier.weight(1f))
        CoverageCount(byVerdict[PlanVerdict.Matches].orEmpty().size, "match it", colors.planMatches, Modifier.weight(1f))
        CoverageCount(byVerdict[PlanVerdict.NotSeen].orEmpty().size, "not seen yet", colors.planNotSeen, Modifier.weight(1f))
    }

    SectionCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), radius = 14.dp, contentPadding = 0.dp) {
        val breaks = byVerdict[PlanVerdict.Breaks]
        val matches = byVerdict[PlanVerdict.Matches]
        breaks?.let { CoverageGroup("Breaks the plan", colors.planBreaks.card.content, colors.error.content, it, divided = false) }
        matches?.let { CoverageGroup("Matches the plan", colors.planMatches.muted, colors.success.content, it, divided = breaks != null) }
        byVerdict[PlanVerdict.NotSeen]?.let { unseen ->
            val shown = if (showAllUnseen) unseen else unseen.take(UNSEEN_PREVIEW)
            CoverageGroup("Not seen yet", scheme.onSurfaceVariant, null, shown, divided = breaks != null || matches != null)
            if (shown.size < unseen.size) {
                HorizontalDivider(thickness = 1.dp, color = scheme.surfaceVariant)
                Text(
                    "Show ${unseen.size - shown.size} more",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { showAllUnseen = true }.padding(10.dp),
                )
            }
        }
    }
    Text(
        "Events the plan doesn't define aren't checked.",
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
    )
}

private const val UNSEEN_PREVIEW = 3

@Composable
private fun SmallOutlinedButton(label: String, content: Color, border: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, border = BorderStroke(1.dp, border), shape = RoundedCornerShape(10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun CoverageCount(count: Int, label: String, verdict: VerdictColors, modifier: Modifier) {
    Surface(color = verdict.card.container, border = BorderStroke(1.dp, verdict.border), shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = verdict.card.content)
            Text(label, style = MaterialTheme.typography.bodySmall, color = verdict.muted)
        }
    }
}

/** One verdict's events; a null [dot] draws the hollow mark of events not seen yet. */
@Composable
private fun CoverageGroup(title: String, titleColor: Color, dot: Color?, events: List<EventCoverage>, divided: Boolean) {
    val scheme = MaterialTheme.colorScheme
    if (divided) HorizontalDivider(thickness = 1.dp, color = scheme.surfaceVariant)
    Text(
        title,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = titleColor,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = if (divided) 12.dp else 10.dp, bottom = 6.dp),
    )
    events.forEach { coverage ->
        HorizontalDivider(thickness = 1.dp, color = scheme.surfaceVariant)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val mark = Modifier.size(8.dp).clip(CircleShape)
            Box(if (dot != null) mark.background(dot) else mark.border(1.5.dp, scheme.outline, CircleShape))
            Text(
                coverage.event.name,
                style = codeTextStyle,
                color = if (dot != null) scheme.onSurface else scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                when (coverage.verdict) {
                    PlanVerdict.Breaks -> "${coverage.event.channel} · ${coverage.breaking} of ${coverage.captured}"
                    PlanVerdict.Matches -> "${coverage.event.channel} · ${coverage.captured} seen"
                    PlanVerdict.NotSeen -> coverage.event.channel
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
