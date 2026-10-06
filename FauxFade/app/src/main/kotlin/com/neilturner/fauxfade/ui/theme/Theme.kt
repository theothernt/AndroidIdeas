package com.neilturner.fauxfade.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme

/**
 * The Material theme the whole screen sits inside.
 *
 * Deliberately carries no colour scheme or typography of its own. Everything on screen is
 * explicit - black surfaces, white text, a fixed 88dp spinner - because the app is a video
 * surface on a television, where a theme that restyles the readouts would only make them harder
 * to read. This exists so the TV Material components have a [MaterialTheme] to resolve against;
 * the defaults are the right answer here.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FauxFadeTheme(content: @Composable () -> Unit) {
	MaterialTheme(content = content)
}
