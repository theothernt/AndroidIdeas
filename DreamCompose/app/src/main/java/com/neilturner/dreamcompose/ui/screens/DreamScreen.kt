package com.neilturner.dreamcompose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * The content of the screen saver. Shown by [com.neilturner.dreamcompose.dream.DreamService] and
 * previewed by [com.neilturner.dreamcompose.dream.DreamActivity].
 */
@Composable
fun DreamScreen(modifier: Modifier = Modifier) {
	Box(
		modifier =
			modifier
				.fillMaxSize()
				.background(Color.Black),
		contentAlignment = Alignment.Center,
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(
				text = "Dream Compose",
				style = MaterialTheme.typography.displayLarge,
				color = Color.White,
				textAlign = TextAlign.Center,
			)
			Text(
				text = "Your screensaver is running",
				style = MaterialTheme.typography.titleLarge,
				color = Color.LightGray,
				textAlign = TextAlign.Center,
			)
		}
	}
}
