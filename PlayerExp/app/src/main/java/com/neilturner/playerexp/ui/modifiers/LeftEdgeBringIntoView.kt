package com.neilturner.playerexp.ui.modifiers

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Scrolls a focused item's leading edge to [leadingInset] from the leading edge of its container.
 *
 * Compose's default only brings a focused item far enough into view, which leaves it against
 * whichever edge it happened to run out of room at. A row using this spec parks the focused card at
 * the same place for every position, so it is always where the eye expects it and the cards still
 * to come stay in view. The inset is separate from the row's content padding, which only positions
 * the first and last cards, because a focused card that is scrolled flush against the screen edge
 * reads as jammed there.
 *
 * Provide it to a lazy layout through [androidx.compose.foundation.gestures.LocalBringIntoViewSpec].
 * A nested layout that wants the normal behaviour can opt back in with `BringIntoViewSpec.Default`.
 */
@Composable
fun rememberLeftEdgeSpec(leadingInset: Dp): BringIntoViewSpec {
    val insetPx = with(LocalDensity.current) { leadingInset.toPx() }
    return remember(insetPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float {
                // An item wider than the row has to hang off the trailing edge instead, otherwise
                // scrolling to its leading edge would leave the far end unreachable.
                val target = if (size > containerSize) containerSize - size else insetPx
                return offset - target
            }
        }
    }
}
