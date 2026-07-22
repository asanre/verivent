package com.snoop.sample

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.snoop.ui.Snoop
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun sampleEngine(): HttpClientEngineFactory<*> = CIO

@Composable
actual fun PlatformExtras() {
    val context = LocalContext.current
    OutlinedButton(onClick = { Snoop.showNotification(context) }, modifier = Modifier.fillMaxWidth()) {
        Text("Show Snoop notification")
    }
}
