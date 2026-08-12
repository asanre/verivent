package com.snoop.ui

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
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import com.snoop.ui.component.AnalyticsRow
import com.snoop.ui.component.FilterChipBar
import com.snoop.ui.component.NetworkRow
import com.snoop.ui.component.SearchPill
import com.snoop.ui.component.SnoopChip
import com.snoop.ui.component.SnoopIcons
import com.snoop.ui.internal.TimelineFilters
import com.snoop.ui.internal.TimelineGroup
import com.snoop.ui.internal.TypeFilter
import com.snoop.ui.internal.groupEntries
import com.snoop.ui.internal.matchesFilters
import com.snoop.ui.internal.stableKey

@Composable
internal fun TimelineTab(
    entries: List<SnoopEntry>,
    groupByEvent: String?,
    labelProperty: String?,
    onOpen: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(TimelineFilters()) }
    var grouped by rememberSaveable { mutableStateOf(groupByEvent != null) }
    val collapsed = remember { mutableStateOf(setOf<String>()) }

    val channels = remember(entries) {
        entries.filterIsInstance<AnalyticsEntry>().map { it.channel }.distinct().sorted()
    }
    // Group over all entries so screen headers survive filters that hide the grouping event.
    val groups = remember(entries, grouped, groupByEvent, labelProperty, filters, query) {
        groupEntries(entries, if (grouped) groupByEvent else null, labelProperty)
            .mapNotNull { group ->
                val shown = group.entries.filter { matchesFilters(it, filters.type, filters.channel, query) }
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (groupByEvent != null) {
                SnoopChip(
                    label = "Group",
                    selected = grouped,
                    onClick = { grouped = !grouped },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            FilterChipBar(
                type = filters.type,
                channel = filters.channel,
                channels = channels,
                onType = { filters = filters.selectType(it) },
                onChannel = { filters = filters.selectChannel(it) },
            )
        }
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxSize().padding(12.dp),
        ) {
            if (groups.isEmpty()) {
                EmptyState(entries.isEmpty())
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    groups.forEach { (groupKey, group) ->
                        val isCollapsed = groupKey in collapsed.value
                        if (grouped && groupByEvent != null && group.screen != null) {
                            item(key = "hdr:$groupKey") {
                                GroupHeader(group, groupByEvent, isCollapsed) {
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
                                        onClick = { onOpen(entry.id) },
                                    )
                                }
                                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: TimelineGroup, groupLabel: String, collapsed: Boolean, onToggle: () -> Unit) {
    val events = group.entries.count { it is AnalyticsEntry }
    val calls = group.entries.count { it is NetworkTransaction }
    val rotation by animateFloatAsState(if (collapsed) 0f else 90f)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = SnoopIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp).rotate(rotation),
        )
        Icon(
            imageVector = SnoopIcons.Phone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            group.screen ?: "session",
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.W600),
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
                groupLabel
            },
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
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
                    imageVector = SnoopIcons.Search,
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
