package com.neilturner.perfview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
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
                    val navController = rememberNavController()
                    PerfViewNavGraph(navController = navController)
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
