package io.github.asanre.verivent.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.asanre.verivent.core.Inspector
import io.github.asanre.verivent.sync.VeriventSync

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Inspector.configure(storageDir = "$filesDir/verivent")
        // Passed at launch so no workspace's key lands in the repo:
        // adb shell am start -n io.github.asanre.verivent.sample/.MainActivity --es verivent_connection '<connection>'
        val connection = intent.getStringExtra("verivent_connection")
        if (savedInstanceState == null && connection != null) {
            runCatching { VeriventSync.connect(connection) }.onFailure { Log.w("VeriventSample", it.message.orEmpty()) }
        }
        setContent { SampleApp() }
    }
}
