package com.neilturner.twopane

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.neilturner.twopane.ui.mainmenu.MainMenuScreen
import com.neilturner.twopane.ui.media.MediaScreen
import com.neilturner.twopane.ui.theme.TwoPaneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TwoPaneTheme {
                var currentScreen by remember { mutableStateOf("main_menu") }

                when (currentScreen) {
                    "main_menu" -> MainMenuScreen(
                        onNavigateToMedia = { currentScreen = "media" }
                    )
                    "media" -> {
                        MediaScreen(onBack = { currentScreen = "main_menu" })
                        BackHandler {
                            currentScreen = "main_menu"
                        }
                    }
                }
            }
        }
    }
}