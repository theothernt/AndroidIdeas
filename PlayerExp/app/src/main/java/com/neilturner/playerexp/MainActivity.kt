package com.neilturner.playerexp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.Surface
import com.neilturner.playerexp.ui.screens.HomeScreen
import com.neilturner.playerexp.ui.screens.PlayerScreen
import com.neilturner.playerexp.ui.theme.PlayerExpTheme

import androidx.media3.common.util.UnstableApi

class MainActivity : ComponentActivity() {
    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlayerExpTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    val currentScreen = remember { mutableStateOf("home") }
                    val selectedMediaType = remember { mutableStateOf("") }

                    when (currentScreen.value) {
                        "home" -> HomeScreen(
                            onNavigateToPlayer = { mediaType ->
                                selectedMediaType.value = mediaType
                                currentScreen.value = "player"
                            }
                        )
                        "player" -> PlayerScreen(
                            mediaType = selectedMediaType.value,
                            onBack = { currentScreen.value = "home" }
                        )
                    }
                }
            }
        }
    }
}
