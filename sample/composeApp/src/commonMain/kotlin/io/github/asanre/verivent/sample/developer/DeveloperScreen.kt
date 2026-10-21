package io.github.asanre.verivent.sample.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.sample.PlatformExtras
import io.github.asanre.verivent.sample.SAMPLE_PLAN
import io.github.asanre.verivent.sample.Sample
import io.github.asanre.verivent.sample.SampleScreen
import io.github.asanre.verivent.sample.Section
import io.github.asanre.verivent.sample.Wide
import io.github.asanre.verivent.sample.webViewerHint
import io.github.asanre.verivent.web.WebViewer
import io.github.asanre.verivent.web.WebViewerBind
import kotlinx.coroutines.launch

/** Verivent itself: the inspector, the shop's tracking plan, and traffic that shows what it catches. */
@Composable
fun DeveloperScreen(onBack: () -> Unit, onInspector: () -> Unit) {
    val scope = rememberCoroutineScope()
    val demos = remember { Demos(Sample.analytics, Sample.client) }
    SampleScreen(title = "Developer", onBack = onBack) {
        Section("Inspector")
        Wide("Open Verivent", tag = "developer.inspector", onClick = onInspector)
        WebViewerControl()
        PlatformExtras()

        Section("Tracking plan")
        Wide("Load the shop's plan", tag = "developer.loadPlan", secondary = true) { Inspector.loadTrackingPlan(SAMPLE_PLAN) }
        Wide("Clear the plan", secondary = true) { Inspector.clearTrackingPlan() }

        Section("Events that break the plan")
        Wide("Purchase without its transaction id", secondary = true, onClick = demos::brokenPurchase)
        Wide("A list of 60 items, 2 off pattern", secondary = true, onClick = demos::longItemList)
        Wide("debug_ping, dropped by the filter", secondary = true, onClick = demos::debugPing)

        Section("Network")
        DemoCall.entries.forEach { call ->
            Wide(call.title, secondary = true) { scope.launch { runCatching { demos.call(call) } } }
        }
    }
}

@Composable
private fun WebViewerControl() {
    var serverUrl by remember {
        mutableStateOf<String?>(if (WebViewer.isRunning) WebViewer.lanUrl() ?: WebViewer.url else null)
    }
    Wide(if (serverUrl != null) "Stop web viewer" else "Start web viewer", secondary = true) {
        if (WebViewer.isRunning) {
            WebViewer.stop()
            serverUrl = null
        } else {
            // start() hands back the reachable URL — the LAN address with its token.
            serverUrl = WebViewer.start(WebViewerBind.Lan)
        }
    }
    serverUrl?.let {
        Column(Modifier.fillMaxWidth().padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Web viewer at $it", style = MaterialTheme.typography.labelMedium)
            Text(webViewerHint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
