package com.neilturner.visualmashup.ui.screens.effect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import com.neilturner.visualmashup.ui.effects.EffectImage
import com.neilturner.visualmashup.ui.effects.TransitionContainer

/**
 * Plays the playlist one image at a time, applying the current effect and transition. The effect,
 * speed and transition are chosen on the main menu.
 */
@Composable
fun EffectScreen(modifier: Modifier = Modifier) {
    val viewModel: EffectViewModel = viewModel()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val effect by viewModel.effect.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val transition by viewModel.transition.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }

        TransitionContainer(
            transition = transition,
            targetState = currentIndex,
            images = viewModel.images,
            widthPx = widthPx,
            heightPx = heightPx,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            EffectImage(
                effect = effect,
                image = viewModel.images[index],
                durationMillis = speed.effectDurationMillis,
                widthPx = widthPx,
                heightPx = heightPx,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = "${effect.displayName}  •  ${speed.displayName}  •  " +
                "${transition.displayName}  •  ${currentIndex + 1}/${viewModel.images.size}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}