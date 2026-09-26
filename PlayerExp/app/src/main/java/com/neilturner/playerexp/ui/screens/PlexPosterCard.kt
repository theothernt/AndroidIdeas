package com.neilturner.playerexp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import com.neilturner.playerexp.ui.modifiers.dpadSelectable
import com.neilturner.playerexp.ui.modifiers.rememberLeftEdgeSpec
import kotlin.math.roundToInt

/**
 * The poster furniture both Plex shelves are built from: the card, the row it sits in and the size
 * they are drawn at. On Deck and the library shelves show the same artwork at the same scale, so
 * they share one implementation rather than drifting apart.
 */

/**
 * The card size, kept as both the dp the layout draws at and the pixels that land on screen. Only
 * the pixel figure is meaningful to Plex and to Coil: handing layout a dp value derived from pixels
 * would scale it by the density a second time and draw the card at twice the size it fetched.
 */
class PosterSize(val height: Dp, val pixels: IntSize)

@Composable
fun rememberPosterSize(): PosterSize {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val density = LocalDensity.current
    return remember(screenHeightDp, density) {
        val height = (screenHeightDp * POSTER_HEIGHT_FRACTION).dp
        val heightPx = with(density) { height.roundToPx() }
        PosterSize(
            height = height,
            pixels = IntSize((heightPx * POSTER_ASPECT_RATIO).roundToInt(), heightPx)
        )
    }
}

/**
 * A horizontal shelf of poster cards.
 *
 * The focused card stops at a fixed inset from the left edge as the row scrolls rather than against
 * whichever edge it happened to run out of room at. Each item is handed the modifier its card should
 * carry; passing [initialFocusRequester] attaches the caller's requester to the first card, which is
 * how the screen decides which shelf the user lands on. Leaving it null keeps this row out of the
 * initial focus race entirely, which matters when a screen stacks several of them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlexPosterRow(
    itemCount: Int,
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester? = null,
    key: (index: Int) -> Any,
    content: @Composable (index: Int, itemModifier: Modifier) -> Unit
) {
    val leftEdgeSpec = rememberLeftEdgeSpec(leadingInset = FOCUSED_CARD_LEADING_INSET)
    CompositionLocalProvider(LocalBringIntoViewSpec provides leftEdgeSpec) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ROW_ITEM_SPACING),
            // 48.dp lines the first poster up with the title; the vertical padding is headroom for
            // the focused card, which grows by 6% and would otherwise be clipped at the top.
            contentPadding = PaddingValues(
                start = SCREEN_HORIZONTAL_PADDING,
                end = SCREEN_HORIZONTAL_PADDING,
                top = FOCUSED_POSTER_OVERHANG,
                bottom = POSTER_ROW_BOTTOM_PADDING
            )
        ) {
            items(count = itemCount, key = { index -> key(index) }) { index ->
                // The requester has to sit on the focusable card itself, so it is handed down
                // rather than applied to a wrapper here.
                content(
                    index,
                    if (index == 0 && initialFocusRequester != null) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

/**
 * Poster only: the image is the card. Focus grows it and a press dips it, both on their own spring
 * so neither waits for the other, and the image fades in over a placeholder so a poster never
 * arrives as a hard edge. [overlay] draws inside the clipped card, which is where On Deck puts its
 * progress bar.
 */
@Composable
fun PlexPosterCard(
    imageUrl: String,
    posterSize: PosterSize,
    modifier: Modifier = Modifier,
    onPressed: () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    var isFocused by remember(imageUrl) { mutableStateOf(false) }
    var selectCount by remember(imageUrl) { mutableIntStateOf(0) }
    val pressScale = remember { Animatable(1f) }
    val focusScale = remember { Animatable(1f) }
    val posterRequest = rememberPosterRequest(imageUrl, posterSize.pixels)
    var posterLoaded by remember(imageUrl) { mutableStateOf(false) }
    val posterAlpha by animateFloatAsState(
        targetValue = if (posterLoaded) 1f else 0f,
        animationSpec = tween(POSTER_FADE_IN_MILLIS),
        label = "posterFadeIn"
    )
    val focusBorder = SolidColor(if (isFocused) CARD_BORDER_COLOR else Color.Transparent)
    val placeholder = MaterialTheme.colorScheme.surfaceVariant

    // Only reading both values inside the layer keeps this to a redraw per frame instead of a
    // recomposition.
    LaunchedEffect(isFocused) {
        focusScale.animateTo(
            targetValue = if (isFocused) FOCUSED_POSTER_SCALE else 1f,
            animationSpec = FOCUS_SCALE_SPRING
        )
    }
    LaunchedEffect(selectCount) {
        if (selectCount > 0) {
            pressScale.animateTo(PRESSED_POSTER_SCALE, tween(PRESS_DOWN_MILLIS))
            pressScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    Box(
        modifier = modifier
            .size(width = posterSize.height * POSTER_ASPECT_RATIO, height = posterSize.height)
            .onFocusChanged { isFocused = it.isFocused }
            .dpadSelectable {
                selectCount++
                onPressed()
            }
            // Scale and rounded clip share one layer: a separate clip would add a second one.
            .graphicsLayer {
                val scale = focusScale.value * pressScale.value
                scaleX = scale
                scaleY = scale
                shape = POSTER_SHAPE
                clip = true
            }
            // Painted under the poster so cards hold their space while Coil is still fetching.
            .drawBehind { drawRect(placeholder) }
            .border(BorderStroke(FOCUS_BORDER_WIDTH, focusBorder), POSTER_SHAPE),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = posterRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = posterAlpha,
                onSuccess = { posterLoaded = true }
            )
        }
        overlay()
    }
}

/**
 * Plex serves the poster at whatever size its thumb token points at, which is usually far larger
 * than the card. [com.neilturner.playerexp.data.plex.PlexImageUrl] asks for a resized copy and states
 * the size the card is drawn at, so what arrives over the network and what Coil decodes are the same
 * dimensions.
 */
@Composable
fun rememberPosterRequest(url: String, posterSize: IntSize): ImageRequest {
    val context = LocalContext.current
    return remember(url, posterSize) {
        ImageRequest.Builder(context)
            .data(url)
            .size(CoilSize(posterSize.width, posterSize.height))
            .build()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StatusMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = SCREEN_HORIZONTAL_PADDING)
    )
}

const val POSTER_ASPECT_RATIO = 2f / 3f

/** Shared left and right inset so the title and the first poster start on the same line. */
val SCREEN_HORIZONTAL_PADDING = 48.dp
val ROW_ITEM_SPACING = 16.dp

/** Room for the focused card to grow past its laid-out height without being clipped. */
val FOCUSED_POSTER_OVERHANG = 12.dp

/**
 * Where the focused card stops when the row scrolls it. The row's own 48.dp content padding only
 * positions the first and last cards, so the focused card needs a wider inset of its own to sit
 * clear of the screen edge.
 */
val FOCUSED_CARD_LEADING_INSET = 96.dp
val POSTER_ROW_BOTTOM_PADDING = 8.dp

/** Posters are sized relative to the screen so a shelf looks the same on any TV. */
const val POSTER_HEIGHT_FRACTION = 0.33f
val POSTER_CORNER_RADIUS = 12.dp
val POSTER_SHAPE = RoundedCornerShape(POSTER_CORNER_RADIUS)
const val FOCUSED_POSTER_SCALE = 1.06f

/** Focus moves on a spring so a card grows and settles instead of snapping to size. */
val FOCUS_SCALE_SPRING: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)
const val PRESSED_POSTER_SCALE = 0.94f
const val PRESS_DOWN_MILLIS = 90
val FOCUS_BORDER_WIDTH = 3.dp
val CARD_BORDER_COLOR = Color.White

/** How long a poster takes to fade in over its placeholder. */
const val POSTER_FADE_IN_MILLIS = 250
