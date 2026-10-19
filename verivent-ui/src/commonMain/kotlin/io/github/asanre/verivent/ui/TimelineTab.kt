package io.github.asanre.verivent.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.core.model.PlanSummary
import io.github.asanre.verivent.core.model.TimelineEntry
import io.github.asanre.verivent.ui.component.AnalyticsRow
import io.github.asanre.verivent.ui.component.FilterChipBar
import io.github.asanre.verivent.ui.component.NetworkRow
import io.github.asanre.verivent.ui.component.PlanStrip
import io.github.asanre.verivent.ui.component.SearchPill
import io.github.asanre.verivent.ui.component.InspectorIcons
import io.github.asanre.verivent.ui.internal.PlanVerdict
import io.github.asanre.verivent.ui.internal.TimelineFilters
import io.github.asanre.verivent.ui.internal.TimelineGroup
import io.github.asanre.verivent.ui.internal.TimelineUiState
import io.github.asanre.verivent.ui.internal.TypeFilter
import io.github.asanre.verivent.ui.internal.breaksPlan
import io.github.asanre.verivent.ui.internal.groupEntries
import io.github.asanre.verivent.ui.internal.matchesFilters
import io.github.asanre.verivent.ui.internal.planCoverage
import io.github.asanre.verivent.ui.internal.stableKey

@Composable
internal fun TimelineTab(
    entries: List<TimelineEntry>,
    plan: PlanSummary?,
    onOpen: (String) -> Unit,
    onOpenPlan: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filters by TimelineUiState.filters
    val collapsed = remember { mutableStateOf(setOf<String>()) }

    val channels = remember(entries) {
        entries.filterIsInstance<AnalyticsEntry>().map { it.channel }.distinct().sorted()
    }
    // Grouped whenever the host's groupBy stamped a group label; otherwise one flat list.
    val grouped = remember(entries) {
        entries.any { it is AnalyticsEntry && it.groupLabel != null }
    }
    val breakingRows = remember(entries, plan) { entries.count { it.breaksPlan(plan) } }
    val coverage = remember(entries, plan) { plan?.let { planCoverage(entries, it) }.orEmpty() }
    // Group over all entries so screen headers survive filters that hide the opening event.
    val groups = remember(entries, grouped, filters, query, plan) {
        groupEntries(entries, grouped)
            .mapNotNull { group ->
                val shown = group.entries.filter { matchesFilters(it, filters, query, plan) }
                if (shown.isEmpty()) null else group.stableKey() to group.copy(entries = shown)
            }
    }

    val listState = rememberLazyListState()
    val newestId = entries.lastOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null && listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchPill(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
        )
        PlanStrip(
            plan = plan,
            breaking = coverage.count { it.verdict == PlanVerdict.Breaks },
            matching = coverage.count { it.verdict == PlanVerdict.Matches },
            onClick = onOpenPlan,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChipBar(
                type = filters.type,
                channel = filters.channel,
                channels = channels,
                onType = { filters = filters.selectType(it) },
                onChannel = { filters = filters.selectChannel(it) },
                // Offered while there is something to show, so a filter left on never hides everything.
                breaking = breakingRows.takeIf { it > 0 || filters.breaksPlan },
                breaksPlan = filters.breaksPlan,
                onBreaksPlan = { filters = filters.toggleBreaksPlan() },
            )
        }
        // Hugs its rows, and scrolls them once they reach the bottom.
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().weight(1f, fill = groups.isEmpty()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            if (groups.isEmpty()) {
                EmptyState(entries.isEmpty())
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    groups.forEach { (groupKey, group) ->
                        val isCollapsed = groupKey in collapsed.value
                        if (grouped && group.screen != null) {
                            item(key = "hdr:$groupKey") {
                                GroupHeader(group, isCollapsed) {
                                    collapsed.value = if (isCollapsed) collapsed.value - groupKey else collapsed.value + groupKey
                                }
                            }
                        }
                        if (!isCollapsed) {
                            items(group.entries, key = { it.id }) { entry ->
                                when (entry) {
                                    is NetworkTransaction -> NetworkRow(entry) { onOpen(entry.id) }
                                    is AnalyticsEntry -> AnalyticsRow(
                                        entry = entry,
                                        plan = plan,
                                        onClick = { onOpen(entry.id) },
                                    )
                                }
                                if (entry !== group.entries.last() || groupKey != groups.last().first) {
                                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: TimelineGroup, collapsed: Boolean, onToggle: () -> Unit) {
    val events = group.entries.count { it is AnalyticsEntry }
    val calls = group.entries.count { it is NetworkTransaction }
    val rotation by animateFloatAsState(if (collapsed) 0f else 90f)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = InspectorIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp).rotate(rotation),
        )
        Text(
            group.screen ?: "session",
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.W600),
            modifier = Modifier.weight(1f),
        )
        Text(
            if (collapsed) {
                buildString {
                    if (events > 0) append("$events event${if (events == 1) "" else "s"}")
                    if (events > 0 && calls > 0) append(" · ")
                    if (calls > 0) append("$calls call${if (calls == 1) "" else "s"}")
                }
            } else {
                group.openedBy.orEmpty()
            },
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun EmptyState(nothingCaptured: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = InspectorIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                if (nothingCaptured) "Nothing captured yet" else "No matches",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                if (nothingCaptured) "Network calls and analytics events will show up here as they happen." else "Try a different filter or search.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
