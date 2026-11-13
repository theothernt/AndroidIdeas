package com.neilturner.visualmashup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
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