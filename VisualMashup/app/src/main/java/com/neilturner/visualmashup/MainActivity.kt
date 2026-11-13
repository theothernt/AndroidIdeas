package com.neilturner.visualmashup

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.neilturner.visualmashup.navigation.AppNavigation
import com.neilturner.visualmashup.ui.components.RamUsageOverlay
import com.neilturner.visualmashup.ui.theme.VisualMashupTheme

class MainActivity : ComponentActivity() {
	@OptIn(ExperimentalTvMaterial3Api::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			VisualMashupTheme {
				Surface(
					modifier = Modifier.fillMaxSize(),
					shape = RectangleShape
				) {
					Box(modifier = Modifier.fillMaxSize()) {
						AppNavigation()
						// Debug builds only: the chip's Debug.getMemoryInfo poll blocks the UI
						// thread (~100 ms here) and stalls every animated screen once a second.
						if (LocalContext.current.applicationInfo.flags and
							ApplicationInfo.FLAG_DEBUGGABLE != 0
						) {
							RamUsageOverlay(
								modifier = Modifier
									.align(Alignment.TopEnd)
									.padding(16.dp),
							)
						}
					}
				}
			}
		}
	}
}