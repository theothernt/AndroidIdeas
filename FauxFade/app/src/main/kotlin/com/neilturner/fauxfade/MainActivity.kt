package com.neilturner.fauxfade

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.neilturner.fauxfade.core.theme.FauxFadeTheme
import com.neilturner.fauxfade.ui.player.AppPhase
import com.neilturner.fauxfade.ui.player.PlayerViewModel
import com.neilturner.fauxfade.ui.player.VideoPlayerScreen
import com.neilturner.fauxfade.ui.startup.StartupScreen

class MainActivity : ComponentActivity() {
	@OptIn(ExperimentalTvMaterial3Api::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			FauxFadeTheme {
				Surface(
					modifier = Modifier.fillMaxSize(),
					shape = RectangleShape,
				) {
					// Hoisted rather than left to VideoPlayerScreen's own default, so both screens
					// share one instance. Two viewModel() calls in the same owner do return the
					// same object, but the picker has to be able to drive the phase the player
					// screen keys off, and this makes that obvious rather than incidental.
					FauxFadeApp(viewModel())
				}
			}
		}
	}
}

/**
 * Chooses between the startup picker and the video loop.
 *
 * The switch is one-way: the picker only ever hands over, and the loop has no way back. Tearing
 * the picker down at the swap also takes any open dropdown with it, so a countdown that runs out
 * while the panel is up cannot leave a popup stranded over the first clip.
 */
@Composable
private fun FauxFadeApp(viewModel: PlayerViewModel) {
	val phase by viewModel.phase.collectAsStateWithLifecycle()
	if (phase == AppPhase.PLAYING) VideoPlayerScreen(viewModel = viewModel) else StartupScreen(viewModel)
}

@Preview(showBackground = true)
@Composable
fun VideoPlayerPreview() {
	FauxFadeTheme {
		VideoPlayerScreen()
	}
}
