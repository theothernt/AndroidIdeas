package com.neilturner.fauxfade

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.neilturner.fauxfade.ui.player.VideoPlayerScreen
import com.neilturner.fauxfade.ui.theme.FauxFadeTheme

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
					VideoPlayerScreen()
				}
			}
		}
	}
}

@Preview(showBackground = true)
@Composable
fun VideoPlayerPreview() {
	FauxFadeTheme {
		VideoPlayerScreen()
	}
}
