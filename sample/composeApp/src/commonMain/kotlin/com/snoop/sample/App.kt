package com.snoop.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snoop.ui.SnoopScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SampleApp() {
    val sampler = remember { Sampler() }
    val scope = rememberCoroutineScope()
    var showInspector by remember { mutableStateOf(false) }

    if (showInspector) {
        BackHandler { showInspector = false }
        SnoopScreen(Modifier.fillMaxSize())
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Snoop sample", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Generate traffic and events, then open the inspector.", style = MaterialTheme.typography.bodyMedium)

        Section("Network")
        Wide("GET success") { scope.launch { runCatching { sampler.getSuccess() } } }
        Wide("GET 404") { scope.launch { runCatching { sampler.getNotFound() } } }
        Wide("POST order") { scope.launch { runCatching { sampler.postOrder() } } }
        Wide("Trigger error") { scope.launch { runCatching { sampler.triggerError() } } }

        Section("Analytics")
        Wide("Log screen_view") { sampler.logScreenView("home") }
        Wide("Log purchase (ecommerce)") { sampler.logPurchase() }
        Wide("Log server-side (gtm)") { sampler.logServerSide() }

        Section("Inspector")
        Wide("Open Snoop (embedded)") { showInspector = true }
        PlatformExtras()
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun Wide(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
