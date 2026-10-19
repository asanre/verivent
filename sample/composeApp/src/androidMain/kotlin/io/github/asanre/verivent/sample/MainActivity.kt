package io.github.asanre.verivent.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.asanre.verivent.core.Inspector

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Inspector.configure(storageDir = "$filesDir/verivent")
        setContent { SampleApp() }
    }
}
