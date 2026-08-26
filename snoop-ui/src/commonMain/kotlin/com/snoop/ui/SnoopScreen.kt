package com.snoop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snoop.core.SnoopCore
import com.snoop.core.model.AnalyticsEntry
import com.snoop.core.model.NetworkTransaction
import com.snoop.core.model.SnoopEntry
import com.snoop.ui.component.SnoopIcons
import com.snoop.ui.component.SnoopToast
import com.snoop.ui.detail.AnalyticsDetailScreen
import com.snoop.ui.detail.NetworkDetailScreen
import com.snoop.ui.internal.entriesToJsonCapped
import com.snoop.ui.theme.LocalSnoopColors
import com.snoop.ui.theme.SnoopTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The whole Snoop inspector as one embeddable composable. Host it anywhere (a screen, a bottom
 * sheet, a dedicated Activity). Reads straight from the shared [SnoopCore] store.
 */
@Composable
fun SnoopScreen(modifier: Modifier = Modifier) {
    SnoopTheme {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // The one place insets are applied: the Surface stays edge to edge so the background
            // paints behind the bars, and only the content moves in. `windowInsetsPadding` is
            // consumption-aware, so a host that already inset us adds nothing on top.
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                SnoopContent()
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SnoopContent() {
    val live by SnoopCore.store.entries.collectAsState()
    var paused by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<SnoopEntry>>(emptyList()) }
    val entries = if (paused) frozen else live

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val selected = entries.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }

    // The detail overlays the timeline instead of replacing it, so TimelineTab never leaves
    // composition and query/filters/collapsed groups/scroll survive opening an entry.
    Box {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // The root already applied safeDrawing; Material3's own contentWindowInsets is not
            // reliably consumption-aware across versions, so it is zeroed deliberately rather than
            // left to double up. Not dead code — don't "clean it up".
            contentWindowInsets = WindowInsets(0),
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = SnoopIcons.LogoMark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        "Snoop",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        fontWeight = FontWeight.SemiBold,
                    )
                    LiveDot(active = !paused && live.isNotEmpty())
                    Text(
                        "${entries.size}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        if (!paused) frozen = live
                        paused = !paused
                    }) {
                        Icon(
                            imageVector = if (paused) SnoopIcons.Play else SnoopIcons.Pause,
                            contentDescription = if (paused) "Resume" else "Pause",
                        )
                    }
                    IconButton(onClick = { SnoopCore.store.clear() }) {
                        Icon(SnoopIcons.Clear, contentDescription = "Clear")
                    }
                    IconButton(onClick = {
                        // Serialize off the main thread and cap before ClipData (binder ~1MB limit).
                        val snapshot = entries
                        scope.launch {
                            val (text, kept) = withContext(Dispatchers.Default) {
                                entriesToJsonCapped(snapshot)
                            }
                            clipboard.setText(AnnotatedString(text))
                            snackbar.showSnackbar(
                                if (kept < snapshot.size) {
                                    "Copied newest $kept of ${snapshot.size} entries as JSON"
                                } else {
                                    "Copied ${snapshot.size} entries as JSON"
                                },
                            )
                        }
                    }) {
                        Icon(SnoopIcons.Export, contentDescription = "Export as JSON")
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) { SnoopToast(it) } },
        ) { padding ->
            // The detail overlays this and swallows touches, but TalkBack and keyboard focus would
            // still walk the timeline underneath it.
            val hidden = if (selected != null) Modifier.clearAndSetSemantics {} else Modifier
            Box(Modifier.padding(padding).then(hidden)) {
                TimelineTab(entries, onOpen = { selectedId = it })
            }
        }
        when (selected) {
            null -> Unit
            is NetworkTransaction -> NetworkDetailScreen(selected, onBack = { selectedId = null })
            is AnalyticsEntry -> AnalyticsDetailScreen(
                entry = selected,
                onBack = { selectedId = null },
            )
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
