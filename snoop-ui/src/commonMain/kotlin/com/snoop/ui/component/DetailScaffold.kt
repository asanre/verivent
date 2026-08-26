package com.snoop.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Floating toast matching the design's inverted-fill notification style. */
@Composable
internal fun SnoopToast(data: SnackbarData) {
    Surface(
        color = MaterialTheme.colorScheme.onSurface,
        contentColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Text(
            text = data.visuals.message,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
internal fun DetailScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.(showMessage: (String) -> Unit) -> Unit = {},
    content: @Composable (modifier: Modifier, showMessage: (String) -> Unit) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // SnoopScreen's root already applied safeDrawing; Material3's own contentWindowInsets is not
        // reliably consumption-aware across versions, so it is zeroed deliberately rather than left
        // to double up. Not dead code — don't "clean it up".
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(SnoopIcons.Back, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                actions(showMessage)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) { SnoopToast(it) } },
    ) { padding ->
        content(Modifier.fillMaxSize().padding(padding), showMessage)
    }
}
