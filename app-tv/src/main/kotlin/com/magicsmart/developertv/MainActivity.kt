package com.magicsmart.developertv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.magicsmart.core.engine.ScanController
import com.magicsmart.core.link.LinkServer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MagicTvApp() }
    }

    override fun onResume() {
        super.onResume()
        ScanController.get(this).onAppResumed()
    }

    override fun onStop() {
        super.onStop()
        LinkServer.get(this).stop()
    }
}
