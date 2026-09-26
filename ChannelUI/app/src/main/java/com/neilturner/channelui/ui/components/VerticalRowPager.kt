package com.neilturner.channelui.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val DEFAULT_PAGE_SETTLE_DURATION_MS = 500

/**
 * A strip of tiles that pages vertically, one row of [itemsPerRow] tiles per page, for D-pad
 * navigation on TV. The strip is meant to be given a bounded height and aligned to the bottom
 * of the screen, with the video or artwork behind it.
 *
 * A plain [VerticalPager] pages on D-pad by moving focus and letting bring-into-view scroll it,
 * and that scroll animation is a fixed spring. This component consumes up/down itself so paging
 * can use [pageAnimationSpec], and moves focus to the same column in the new row.
 *
 * @param itemCount total number of tiles
 * @param itemsPerRow number of tiles shown on each page
 * @param pageAnimationSpec animation used for D-pad paging and for settling a drag fling
 * @param itemSpacing gap between tiles in a row
 * @param itemContent tile content, called with the tile's index and a modifier that already
 *   carries that tile's row weight and focus tracking
 */
@Composable
fun VerticalRowPager(
    itemCount: Int,
    itemsPerRow: Int,
    modifier: Modifier = Modifier,
    pageAnimationSpec: AnimationSpec<Float> = tween(
        durationMillis = DEFAULT_PAGE_SETTLE_DURATION_MS,
        easing = FastOutSlowInEasing
    ),
    itemSpacing: Dp = 8.dp,
    itemContent: @Composable RowScope.(itemIndex: Int, itemModifier: Modifier) -> Unit
) {
    val pageCount = (itemCount + itemsPerRow - 1) / itemsPerRow
    val pagerState = rememberPagerState(initialPage = 0) { pageCount }
    val coroutineScope = rememberCoroutineScope()

    val focusRequesters = remember(itemCount) { List(itemCount) { FocusRequester() } }
    var focusedIndex by remember { mutableStateOf(0) }
    val itemModifiers = remember(itemCount) {
        List(itemCount) { index ->
            Modifier
                .focusRequester(focusRequesters[index])
                .onFocusChanged { if (it.isFocused) focusedIndex = index }
        }
    }

    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapAnimationSpec = pageAnimationSpec
    )

    val dpadPaging = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val step = when (event.key) {
            Key.DirectionDown -> itemsPerRow
            Key.DirectionUp -> -itemsPerRow
            else -> return@onPreviewKeyEvent false
        }
        val target = (focusedIndex + step).coerceIn(0, itemCount - 1)
        if (target == focusedIndex) return@onPreviewKeyEvent false
        focusedIndex = target
        coroutineScope.launch {
            pagerState.animateScrollToPage(
                page = target / itemsPerRow,
                animationSpec = pageAnimationSpec
            )
            // A newer key press may already have retargeted us, so only claim focus if not
            if (focusedIndex == target) focusRequesters[target].requestFocus()
        }
        true
    }

    VerticalPager(
        state = pagerState,
        modifier = modifier.then(dpadPaging),
        pageSpacing = 0.dp,
        beyondViewportPageCount = 1,
        flingBehavior = flingBehavior
    ) { page ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(itemsPerRow) { column ->
                    val index = page * itemsPerRow + column
                    if (index < itemCount) {
                        itemContent(index, Modifier.weight(1f).then(itemModifiers[index]))
                    } else {
                        // Fill the trailing slots so every page lines up
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
