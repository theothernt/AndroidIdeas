package com.neilturner.fauxfade.feature.preloadedframe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neilturner.fauxfade.BuildConfig
import com.neilturner.fauxfade.core.media.Quality
import com.neilturner.fauxfade.core.player.AppPhase
import com.neilturner.fauxfade.core.player.ui.MemoryReadout
import com.neilturner.fauxfade.core.player.ui.PlayingQualityLabel
import com.neilturner.fauxfade.core.player.ui.SlotPlayerSurface
import com.neilturner.fauxfade.core.player.ui.StartupLoader
import com.neilturner.fauxfade.core.player.ui.StillOverlay
import com.neilturner.fauxfade.core.player.ui.TransitionCountdown

/** The preloaded-frame playback half: one surface, a still dissolving in over a still-playing clip. */
@Composable
fun PreloadedFrameScreen(
	viewModel: PreloadedFrameViewModel,
	initialQuality: Quality? = null,
	modifier: Modifier = Modifier,
) {
	LaunchedEffect(initialQuality) {
		if (initialQuality != null) viewModel.startWith(initialQuality)
	}
	val phase by viewModel.phase.collectAsStateWithLifecycle()
	if (phase != AppPhase.PLAYING) {
		Box(modifier.fillMaxSize().background(Color.Black))
		return
	}
	val loading by viewModel.loading.collectAsStateWithLifecycle()

	DisposableEffect(viewModel) {
		viewModel.start()
		onDispose { viewModel.stop() }
	}

	Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
		viewModel.slots.forEach { slot ->
			SlotPlayerSurface(
				player = viewModel.playerFor(slot),
				visible = true,
				modifier = Modifier.fillMaxSize(),
			)
		}
		val stillFrame by viewModel.stillFrame.collectAsStateWithLifecycle()
		val stillPhase by viewModel.stillPhase.collectAsStateWithLifecycle()
		StillOverlay(stillFrame, stillPhase, DISSOLVE_MS.toInt(), modifier = Modifier.fillMaxSize())
		if (BuildConfig.DEBUG) {
			val memoryMb by viewModel.memoryMb.collectAsStateWithLifecycle()
			MemoryReadout(memoryMb, Modifier.align(Alignment.TopStart))
		}
		val seconds by viewModel.remainingSeconds.collectAsStateWithLifecycle()
		TransitionCountdown(seconds, Modifier.align(Alignment.TopEnd))
		val quality by viewModel.playingQuality.collectAsStateWithLifecycle()
		PlayingQualityLabel(quality, Modifier.align(Alignment.BottomEnd))
		StartupLoader(loading, Modifier.fillMaxSize())
	}
}

private const val DISSOLVE_MS = 750L
