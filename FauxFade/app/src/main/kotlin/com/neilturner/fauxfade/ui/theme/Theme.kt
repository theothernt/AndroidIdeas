package com.neilturner.fauxfade.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * The Material theme the whole app sits inside.
 *
 * A near-black palette, because the app is a video surface on a television: anything lighter
 * than this shows up as a glowing rectangle around the picture, and the window behind the video
 * is already black so there is nothing to blend into.
 *
 * The one letterbox black is deliberately not a slot - see [VideoPlayerScreen], which keeps it
 * as a literal because a video edge has to be true black rather than the app's off-black.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FauxFadeTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = FauxFadeColorScheme,
		content = content,
	)
}

// The palette, declared before the scheme that reads it. Top-level properties initialise in
// file order, so a scheme above these would capture them uninitialised.

/** The app backdrop: not pure black, so the panel and the buttons have something to sit against. */
private val BACKDROP = Color(0xFF0E0E10)

/** Panels and other raised, flat surfaces. */
private val PANEL = Color(0xFF1C1B20)

/** Text and icons. Not pure white - a shade softer reads better at viewing distance. */
private val TEXT = Color(0xFFE6E0E9)

/** Focused components. Light and desaturated, because TV Material signals focus by swapping
 *  the focused component to `primary`, and it has to read as "this one" from across a room. */
private val FOCUS = Color(0xFFBCB4C6)

/** Ink on top of a focused component. */
private val INK = Color(0xFF1C1B20)

/** The selected-but-not-focused row in the quality panel. */
private val SELECTED_ROW = Color(0xFF332F3B)

/** The focused row in the quality panel. */
private val FOCUSED_ROW = Color(0xFF4A4654)

/**
 * The popup dim. Black at partial alpha - a scheme slot is an opaque colour, so the alpha is
 * applied with `copy(alpha =)` at the call site.
 */
private val SCRIM = Color.Black

/**
 * Only the slots the app actually reaches for are set. Everything else keeps the dark defaults,
 * which are already tuned for a dark room and are the right answer on a TV.
 *
 * The correspondence the screens rely on:
 * - `background` is the app backdrop behind the picker
 * - `surface` is the open quality panel
 * - `scrim` is dimmed over the controls while the panel is open, and backs the readout chips
 * - `primaryContainer` / `surfaceVariant` are the focused and selected rows inside the panel
 */
private val FauxFadeColorScheme =
	darkColorScheme(
		primary = FOCUS,
		onPrimary = INK,
		primaryContainer = FOCUSED_ROW,
		onPrimaryContainer = TEXT,
		surface = PANEL,
		onSurface = TEXT,
		surfaceVariant = SELECTED_ROW,
		onSurfaceVariant = TEXT,
		background = BACKDROP,
		onBackground = TEXT,
		scrim = SCRIM,
	)
