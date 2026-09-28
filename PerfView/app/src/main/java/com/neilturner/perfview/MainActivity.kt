package com.neilturner.perfview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.neilturner.perfview.overlay.CpuOverlayService
import com.neilturner.perfview.ui.navigation.PerfViewNavGraph
import com.neilturner.perfview.ui.theme.PerfViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PerfViewTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PerfViewNavGraph()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Tear the overlay down only when it is actually attached. Returning from the
        // overlay-permission Settings screen, and the very first resume, both reach
        // this callback with no overlay running, and an unconditional stopService
        // there would race the StartBackgroundOverlay command.
        if (CpuOverlayService.isRunning) {
            stopService(CpuOverlayService.createStopIntent(this))
        }
    }
}
