package com.neilturner.twopane

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.neilturner.twopane.ui.mainmenu.MainMenuScreen
import com.neilturner.twopane.ui.theme.TwoPaneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TwoPaneTheme {
                MainMenuScreen()
            }
        }
    }
}