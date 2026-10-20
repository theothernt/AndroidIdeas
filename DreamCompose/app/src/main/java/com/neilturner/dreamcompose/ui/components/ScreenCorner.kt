package com.neilturner.dreamcompose.ui.components

import androidx.compose.ui.Alignment

/**
 * The corners of the screen the clock and the countdown can be in.
 */
internal enum class ScreenCorner(
	val alignment: Alignment,
	val isEnd: Boolean,
	val isTop: Boolean,
) {
	BottomStart(Alignment.BottomStart, isEnd = false, isTop = false),
	BottomEnd(Alignment.BottomEnd, isEnd = true, isTop = false),
	TopEnd(Alignment.TopEnd, isEnd = true, isTop = true),
	TopStart(Alignment.TopStart, isEnd = false, isTop = true),
}

/**
 * The corner diagonally opposite to this one.
 */
internal val ScreenCorner.opposite: ScreenCorner
	get() =
		when (this) {
			ScreenCorner.BottomStart -> ScreenCorner.TopEnd
			ScreenCorner.BottomEnd -> ScreenCorner.TopStart
			ScreenCorner.TopEnd -> ScreenCorner.BottomStart
			ScreenCorner.TopStart -> ScreenCorner.BottomEnd
		}
