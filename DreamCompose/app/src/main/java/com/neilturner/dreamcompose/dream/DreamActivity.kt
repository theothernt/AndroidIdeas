package com.neilturner.dreamcompose.dream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.neilturner.dreamcompose.ui.screens.DreamScreen
import com.neilturner.dreamcompose.ui.theme.DreamComposeTheme

/**
 * Renders exactly the same content as [DreamService] so the screensaver can be previewed from
 * inside the app without having to leave it for the system screen saver settings.
 */
class DreamActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			DreamComposeTheme {
				DreamScreen()
			}
		}
	}
}
