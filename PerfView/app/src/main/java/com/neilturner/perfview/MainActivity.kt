package com.neilturner.perfview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.neilturner.perfview.overlay.CpuOverlayService
import com.neilturner.perfview.ui.dashboard.PerfViewRoute
import com.neilturner.perfview.ui.theme.PerfViewTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PerfViewTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PerfViewRoute()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Stop overlay service when app comes to foreground
        stopService(CpuOverlayService.createStopIntent(this))
    }
}
