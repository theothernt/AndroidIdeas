package com.neilturner.inputtest.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neilturner.inputtest.ui.components.TvActionButton

/**
 * No view model here on purpose: this screen holds no state beyond its own navigation
 * callbacks, so adding one would only add indirection.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
	onOpenInputScreen: () -> Unit,
	onOpenTextFieldExamples: () -> Unit,
	modifier: Modifier = Modifier
) {
	Surface(
		modifier = modifier.fillMaxSize(),
		shape = RectangleShape
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(48.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			Text(
				text = "Input Test",
				style = MaterialTheme.typography.headlineLarge
			)

			Spacer(modifier = Modifier.height(8.dp))

			Text(
				text = "Pick a screen to try out different text input approaches.",
				style = MaterialTheme.typography.bodyMedium,
				color = Color.LightGray
			)

			Spacer(modifier = Modifier.height(32.dp))

			TvActionButton(
				text = "Input Screen",
				onClick = onOpenInputScreen
			)

			Spacer(modifier = Modifier.height(20.dp))

			TvActionButton(
				text = "Text Field Examples",
				onClick = onOpenTextFieldExamples
			)
		}
	}
}
