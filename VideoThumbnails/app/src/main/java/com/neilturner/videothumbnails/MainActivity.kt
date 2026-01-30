package com.neilturner.videothumbnails

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.neilturner.videothumbnails.ui.screens.HomeScreen
import com.neilturner.videothumbnails.ui.theme.DarkGray
import com.neilturner.videothumbnails.ui.theme.VideoThumbnailsTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            var showDarkGrey by remember { mutableStateOf(false) }
            val backgroundColor by animateColorAsState(
                targetValue = if (showDarkGrey) DarkGray else Color.Black,
                animationSpec = tween(durationMillis = 1000),
                label = "backgroundColorTransition"
            )

            LaunchedEffect(Unit) {
                showDarkGrey = true
            }

            VideoThumbnailsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = SurfaceDefaults.colors(containerColor = backgroundColor)
                ) {
                    HomeScreen()
                }
            }
        }
    }
}

