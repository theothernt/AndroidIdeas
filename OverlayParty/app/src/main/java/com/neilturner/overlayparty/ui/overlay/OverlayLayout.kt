package com.neilturner.overlayparty.ui.overlay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.compose.ui.Alignment

/**
 * A flexible layout composable that positions overlays in the 4 screen corners.
 *
 * Uses SubcomposeLayout to measure overlays and allocate space dynamically.
 * The start (left) overlay is measured first and takes only what it needs,
 * while the end (right) overlay can expand to fill remaining space.
 *
 * @param topStart Content for the top-left corner
 * @param topEnd Content for the top-right corner
 * @param bottomStart Content for the bottom-left corner
 * @param bottomEnd Content for the bottom-right corner
 * @param modifier Modifier for the entire layout
 */
@Composable
fun OverlayLayout(
    topStart: OverlayContent?,
    topEnd: OverlayContent?,
    bottomStart: OverlayContent?,
    bottomEnd: OverlayContent?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top row with adaptive spacing - Top aligned
        AdaptiveOverlayRow(
            startContent = topStart,
            endContent = topEnd,
            verticalAlignment = Alignment.Top,
            showBackground = false
        )

        // Flexible spacer pushes bottom row to the bottom
        Spacer(modifier = Modifier.weight(1f))

        // Bottom row with adaptive spacing - Bottom aligned
        AdaptiveOverlayRow(
            startContent = bottomStart,
            endContent = bottomEnd,
            verticalAlignment = Alignment.Bottom,
            showBackground = true
        )
    }
}

/**
 * An adaptive row that measures the start overlay first (wraps content),
 * then gives the remaining space to the end overlay.
 *
 * This allows one overlay to expand when the other has less content.
 *
 * @param startContent Content for the left/start position
 * @param endContent Content for the right/end position
 * @param minGap Minimum gap between overlays
 * @param verticalAlignment Vertical alignment for items in the row
 * @param showBackground Whether to show background for overlays in this row
 */
@Composable
private fun AdaptiveOverlayRow(
    startContent: OverlayContent?,
    endContent: OverlayContent?,
    minGap: Dp = 16.dp,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    showBackground: Boolean = true
) {
    SubcomposeLayout { constraints ->
        val gapPx = minGap.roundToPx()

        // Measure start overlay first - it wraps to its content size
        // but is capped at 50% of available width to prevent it from taking everything
        val maxStartWidth = (constraints.maxWidth - gapPx) / 2
        val startPlaceable = subcompose("start") {
            OverlaySlot(content = startContent, showBackground = showBackground)
        }.firstOrNull()?.measure(
            Constraints(
                minWidth = 0,
                maxWidth = maxStartWidth.coerceAtLeast(0),
                minHeight = 0,
                maxHeight = constraints.maxHeight
            )
        )

        val startWidth = startPlaceable?.width ?: 0

        // End overlay gets remaining space after start overlay and gap
        val endMaxWidth = constraints.maxWidth - startWidth - gapPx
        val endPlaceable = subcompose("end") {
            OverlaySlot(content = endContent, showBackground = showBackground)
        }.firstOrNull()?.measure(
            Constraints(
                minWidth = 0,
                maxWidth = endMaxWidth.coerceAtLeast(0),
                minHeight = 0,
                maxHeight = constraints.maxHeight
            )
        )

        val endWidth = endPlaceable?.width ?: 0
        val height = maxOf(startPlaceable?.height ?: 0, endPlaceable?.height ?: 0)

        layout(constraints.maxWidth, height) {
            // Place start overlay
            val startY = verticalAlignment.align(startPlaceable?.height ?: 0, height)
            startPlaceable?.placeRelative(0, startY)

            // Place end overlay
            val endY = verticalAlignment.align(endPlaceable?.height ?: 0, height)
            endPlaceable?.placeRelative(constraints.maxWidth - endWidth, endY)
        }
    }
}
