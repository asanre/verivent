package com.snoop.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.snoop.core.SnoopCore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SnoopCore.configure(storageDir = "$filesDir/snoop")
        setContent { SampleApp() }
    }
}
