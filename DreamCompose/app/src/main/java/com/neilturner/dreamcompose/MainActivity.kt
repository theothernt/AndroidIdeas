package com.neilturner.dreamcompose

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.Surface
import com.neilturner.dreamcompose.dream.DreamActivity
import com.neilturner.dreamcompose.dream.DreamService
import com.neilturner.dreamcompose.ui.screens.MainScreen
import com.neilturner.dreamcompose.ui.screens.SetupHelpOverlay
import com.neilturner.dreamcompose.ui.theme.DreamComposeTheme

/**
 * The app entry point. It owns the wiring between the system and the Compose screens, and
 * nothing else: everything it shows lives in [com.neilturner.dreamcompose.ui.screens].
 */
class MainActivity : ComponentActivity() {
	private var isScreenSaverEnabled by mutableStateOf(false)
	private var showSetupHelp by mutableStateOf(false)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			DreamComposeTheme {
				Surface(
					modifier = Modifier.fillMaxSize(),
					shape = RectangleShape,
				) {
					MainScreen(
						isScreenSaverEnabled = isScreenSaverEnabled,
						onPreviewDream = {
							startActivity(Intent(this@MainActivity, DreamActivity::class.java))
						},
						onOpenDreamSettings = {
							if (!openDreamSettings()) {
								showSetupHelp = true
							}
						},
					)

					if (showSetupHelp) {
						SetupHelpOverlay(onDismiss = { showSetupHelp = false })
					}
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		// The user may have changed the screen saver in the system settings while this activity
		// was in the background, so the status is read every time the app comes to the front.
		isScreenSaverEnabled = readIsScreenSaverEnabled()
	}

	/**
	 * Opens the system screen saver settings. Returns false when the device does not have them,
	 * which is the case on most Android TV builds.
	 */
	private fun openDreamSettings(): Boolean =
		try {
			startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
			true
		} catch (_: ActivityNotFoundException) {
			false
		}

	/**
	 * Whether this app is the screen saver the system would show. The setting holds a colon
	 * separated list of flattened component names.
	 */
	private fun readIsScreenSaverEnabled(): Boolean {
		val components = Settings.Secure.getString(contentResolver, SCREENSAVER_COMPONENTS) ?: return false
		val dreamService = ComponentName(this, DreamService::class.java)
		return components.split(':').any { ComponentName.unflattenFromString(it) == dreamService }
	}

	private companion object {
		// Settings.Secure.SCREENSAVER_COMPONENTS is not public API.
		const val SCREENSAVER_COMPONENTS = "screensaver_components"
	}
}
