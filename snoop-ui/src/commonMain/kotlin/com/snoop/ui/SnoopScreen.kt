package com.snoop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snoop.core.SnoopCore
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import com.snoop.ui.component.AnalyticsRow
import com.snoop.ui.component.FilterChipBar
import com.snoop.ui.component.NetworkRow
import com.snoop.ui.component.SnoopChip
import com.snoop.ui.detail.AnalyticsDetailScreen
import com.snoop.ui.detail.NetworkDetailScreen
import com.snoop.ui.internal.TimelineFilters
import com.snoop.ui.internal.TimelineGroup
import com.snoop.ui.internal.entriesToJson
import com.snoop.ui.internal.groupEntries
import com.snoop.ui.theme.LocalSnoopColors
import com.snoop.ui.theme.SnoopTheme
import kotlinx.coroutines.launch

internal enum class TypeFilter { All, Network, Analytics }

/**
 * The whole Snoop inspector as one embeddable composable. Host it anywhere (a screen, a bottom
 * sheet, a dedicated Activity). Reads straight from the shared [SnoopCore] store.
 *
 * @param groupByEvent analytics event name that starts a new collapsible screen group in the
 *   timeline (default `"screen_view"`); pass `null` to disable grouping.
 */
@Composable
fun SnoopScreen(modifier: Modifier = Modifier, groupByEvent: String? = "screen_view") {
    SnoopTheme {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SnoopContent(groupByEvent)
        }
    }
}

@Composable
private fun SnoopContent(groupByEvent: String?) {
    val live by SnoopCore.store.entries.collectAsState()
    var paused by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<SnoopEntry>>(emptyList()) }
    val entries = if (paused) frozen else live

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val selected = entries.firstOrNull { it.id == selectedId }
    if (selected != null) {
        when (selected) {
            is NetworkTransaction -> NetworkDetailScreen(selected, onBack = { selectedId = null })
            is AnalyticsEntry -> AnalyticsDetailScreen(
                entry = selected,
                allEntries = entries,
                onOpenCall = { selectedId = it },
                onBack = { selectedId = null },
            )
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Snoop", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                LiveDot(active = !paused && live.isNotEmpty())
                Text(
                    "${entries.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    if (!paused) frozen = live
                    paused = !paused
                }) {
                    Icon(
                        imageVector = if (paused) Icons.Default.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = if (paused) "Resume" else "Pause",
                    )
                }
                IconButton(onClick = { SnoopCore.store.clear() }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Clear")
                }
                IconButton(onClick = {
                    val text = entriesToJson(entries)
                    clipboard.setText(AnnotatedString(text))
                    scope.launch { snackbar.showSnackbar("Copied ${entries.size} entries as JSON") }
                }) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = "Export as JSON")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            TimelineTab(entries, groupByEvent, onOpen = { selectedId = it })
        }
    }
}

@Composable
private fun LiveDot(active: Boolean) {
    val colors = LocalSnoopColors.current
    val color by animateColorAsState(
        if (active) colors.liveDot else MaterialTheme.colorScheme.outline,
    )
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun TimelineTab(entries: List<SnoopEntry>, groupByEvent: String?, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(TimelineFilters()) }
    var grouped by rememberSaveable { mutableStateOf(groupByEvent != null) }
    val collapsed = remember { mutableStateOf(setOf<String>()) }

    val channels = remember(entries) {
        entries.filterIsInstance<AnalyticsEntry>().map { it.channel }.distinct().sorted()
    }
    val callsById = remember(entries) {
        entries.filterIsInstance<NetworkTransaction>().associateBy { it.id }
    }
    // Group over all entries so screen headers survive filters that hide the grouping event.
    val groups = groupEntries(entries, if (grouped) groupByEvent else null)
        .mapNotNull { group ->
            val shown = group.entries.filter { matchesFilters(it, filters.type, filters.channel, query) }
            if (shown.isEmpty()) null else group.entries.first().id to group.copy(entries = shown)
        }

    val listState = rememberLazyListState()
    val newestId = entries.lastOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null && listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search path, host, event…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
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
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
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
                                        correlatedCall = entry.correlatedCallId?.let(callsById::get),
                                        onClick = { onOpen(entry.id) },
                                    )
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (collapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text("📱", style = MaterialTheme.typography.labelLarge)
        Text(
            group.screen ?: "session",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
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
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

private fun matchesFilters(
    entry: SnoopEntry,
    type: TypeFilter,
    channel: String?,
    query: String,
): Boolean {
    val typeOk = when (type) {
        TypeFilter.All -> true
        TypeFilter.Network -> entry is NetworkTransaction
        TypeFilter.Analytics -> entry is AnalyticsEntry
    }
    if (!typeOk) return false
    if (channel != null && !(entry is AnalyticsEntry && entry.channel == channel)) return false
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return when (entry) {
        is NetworkTransaction ->
            entry.path.lowercase().contains(q) ||
                entry.host.lowercase().contains(q) ||
                entry.method.lowercase().contains(q)
        is AnalyticsEntry ->
            entry.name.lowercase().contains(q) || entry.channel.lowercase().contains(q)
    }
}

@Composable
private fun EmptyState(nothingCaptured: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("👀", style = MaterialTheme.typography.displaySmall)
            Text(
                if (nothingCaptured) "Nothing captured yet" else "No matches",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
