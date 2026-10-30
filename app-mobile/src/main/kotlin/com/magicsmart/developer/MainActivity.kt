package com.magicsmart.developer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.magicsmart.core.engine.ScanController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MagicMobileApp() }
    }

    override fun onResume() {
        super.onResume()
        ScanController.get(this).onAppResumed()
    }
}
