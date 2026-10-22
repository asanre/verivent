package io.github.asanre.verivent.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.core.model.AnalyticsEntry
import io.github.asanre.verivent.core.model.NetworkTransaction
import io.github.asanre.verivent.core.model.TimelineEntry
import io.github.asanre.verivent.ui.component.InspectorIcons
import io.github.asanre.verivent.ui.component.InspectorToast
import io.github.asanre.verivent.ui.detail.AnalyticsDetailScreen
import io.github.asanre.verivent.ui.detail.NetworkDetailScreen
import io.github.asanre.verivent.ui.internal.entriesToJsonCapped
import io.github.asanre.verivent.ui.theme.LocalInspectorColors
import io.github.asanre.verivent.ui.theme.InspectorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The whole Verivent inspector as one embeddable composable. Host it anywhere (a screen, a bottom
 * sheet, a dedicated Activity). Reads straight from the shared [Inspector] store. With [onClose],
 * the top bar shows a back button that calls it.
 */
@Composable
fun InspectorScreen(modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    InspectorTheme {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // The one place insets are applied: the Surface stays edge to edge so the background
            // paints behind the bars, and only the content moves in. `windowInsetsPadding` is
            // consumption-aware, so a host that already inset us adds nothing on top.
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                InspectorContent(onClose)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InspectorContent(onClose: (() -> Unit)?) {
    val live by Inspector.store.entries.collectAsState()
    var paused by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<TimelineEntry>>(emptyList()) }
    val entries = if (paused) frozen else live

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val plan by Inspector.trackingPlan.collectAsState()
    var planOpen by rememberSaveable { mutableStateOf(false) }

    val selected = entries.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null || planOpen) {
        if (selected != null) selectedId = null else planOpen = false
    }

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (onClose != null) 4.dp else 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onClose != null) {
                        IconButton(onClick = onClose, modifier = Modifier.padding(end = 2.dp).size(44.dp)) {
                            Icon(InspectorIcons.Back, contentDescription = "Close inspector", modifier = Modifier.size(22.dp))
                        }
                    }
                    Text(
                        Inspector.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                    )
                    LiveDot(active = !paused && live.isNotEmpty())
                    Text(
                        "${entries.size}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            if (!paused) frozen = live
                            paused = !paused
                        },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = if (paused) InspectorIcons.Play else InspectorIcons.Pause,
                            contentDescription = if (paused) "Resume" else "Pause",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = { Inspector.store.clear() }, modifier = Modifier.size(44.dp)) {
                        Icon(InspectorIcons.Clear, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                    }
                    IconButton(modifier = Modifier.size(44.dp), onClick = {
                        // Serialize off the main thread and cap before ClipData (binder ~1MB limit).
                        val snapshot = entries
                        scope.launch {
                            val (text, kept, events) = withContext(Dispatchers.Default) {
                                entriesToJsonCapped(snapshot)
                            }
                            if (events > 0) clipboard.setText(AnnotatedString(text))
                            snackbar.showSnackbar(
                                when {
                                    events == 0 -> "No analytics events to export"
                                    kept < events -> "Copied newest $kept of $events events as JSON"
                                    else -> "Copied $events events as JSON"
                                },
                            )
                        }
                    }) {
                        Icon(InspectorIcons.Export, contentDescription = "Export as JSON", modifier = Modifier.size(20.dp))
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) { InspectorToast(it) } },
        ) { padding ->
            // The detail overlays this and swallows touches, but TalkBack and keyboard focus would
            // still walk the timeline underneath it.
            val hidden = if (selected != null || planOpen) Modifier.clearAndSetSemantics {} else Modifier
            Box(Modifier.padding(padding).then(hidden)) {
                TimelineTab(entries, plan, onOpen = { selectedId = it }, onOpenPlan = { planOpen = true })
            }
        }
        if (planOpen) TrackingPlanScreen(entries, onBack = { planOpen = false })
        when (selected) {
            null -> Unit
            is NetworkTransaction -> NetworkDetailScreen(selected, onBack = { selectedId = null })
            is AnalyticsEntry -> AnalyticsDetailScreen(
                entry = selected,
                plan = plan,
                onBack = { selectedId = null },
            )
        }
    }
}

@Composable
private fun LiveDot(active: Boolean) {
    val colors = LocalInspectorColors.current
    val color by animateColorAsState(
        if (active) colors.liveDot else MaterialTheme.colorScheme.outline,
    )
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}
