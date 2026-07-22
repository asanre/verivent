package com.snoop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.snoop.ui.detail.AnalyticsDetailScreen
import com.snoop.ui.detail.NetworkDetailScreen
import com.snoop.ui.summary.SummaryContent
import com.snoop.ui.theme.SnoopTheme

internal enum class SnoopTab(val label: String, val glyph: String) {
    Timeline("Timeline", "▤"),
    Summary("Summary", "▦"),
    Settings("Settings", "⚙"),
}

internal enum class TypeFilter { All, Network, Analytics }

/**
 * The whole Snoop inspector as one embeddable composable. Host it anywhere (a screen, a bottom
 * sheet, a dedicated Activity). Reads straight from the shared [SnoopCore] store.
 */
@Composable
fun SnoopScreen(modifier: Modifier = Modifier) {
    SnoopTheme {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SnoopContent()
        }
    }
}

@Composable
private fun SnoopContent() {
    val live by SnoopCore.store.entries.collectAsState()
    var paused by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<SnoopEntry>>(emptyList()) }
    val entries = if (paused) frozen else live

    var tab by remember { mutableStateOf(SnoopTab.Timeline) }
    var selectedId by remember { mutableStateOf<String?>(null) }

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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Snoop", fontWeight = FontWeight.Bold)
                        Text(
                            "${entries.size} entries",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (!paused) frozen = live
                        paused = !paused
                    }) { Text(if (paused) "▶" else "⏸") }
                    IconButton(onClick = { SnoopCore.store.clear() }) { Text("🗑") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                SnoopTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Text(entry.glyph) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                SnoopTab.Timeline -> TimelineTab(entries, onOpen = { selectedId = it })
                SnoopTab.Summary -> SummaryContent(entries)
                SnoopTab.Settings -> SettingsContent()
            }
        }
    }
}

@Composable
private fun TimelineTab(entries: List<SnoopEntry>, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(TypeFilter.All) }
    var channelFilter by remember { mutableStateOf<String?>(null) }

    val channels = remember(entries) {
        entries.filterIsInstance<AnalyticsEntry>().map { it.channel }.distinct().sorted()
    }
    val filtered = entries
        .filter { matchesFilters(it, typeFilter, channelFilter, query) }
        .asReversed()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search path, host, event…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
        FilterChipBar(typeFilter, channelFilter, channels, onType = { typeFilter = it }, onChannel = { channelFilter = it })
        if (filtered.isEmpty()) {
            EmptyState(entries.isEmpty())
        } else {
            LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { entry ->
                    when (entry) {
                        is NetworkTransaction -> NetworkRow(entry) { onOpen(entry.id) }
                        is AnalyticsEntry -> AnalyticsRow(entry) { onOpen(entry.id) }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
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

@Composable
private fun SettingsContent() {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Buffer capacity", fontWeight = FontWeight.SemiBold)
                Text(
                    "Keeps the most recent ${SnoopCore.store.capacity} entries in memory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Snoop is open source — Apache-2.0", style = MaterialTheme.typography.bodySmall)
        }
    }
}
