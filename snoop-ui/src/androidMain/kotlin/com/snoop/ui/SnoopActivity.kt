package com.snoop.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Full-screen host for the inspector, launched from the notification or `Snoop.open(context)`. */
class SnoopActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SnoopScreen() }
    }
}
