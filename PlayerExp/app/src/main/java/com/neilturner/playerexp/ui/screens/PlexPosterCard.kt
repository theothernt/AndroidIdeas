package com.neilturner.playerexp.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Border
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
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

@Composable
fun rememberHeaderPosterSize(): PosterSize {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val density = LocalDensity.current
    return remember(screenHeightDp, density) {
        val height = (screenHeightDp * HEADER_POSTER_HEIGHT_FRACTION).dp.coerceAtMost(HEADER_POSTER_MAX_HEIGHT)
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
 * whichever edge it happened to run out of room at.
 *
 * The first card takes focus so the row is usable the moment it appears, and only then: the request
 * is made once per composition, because the list behind this row is replaced while the user is
 * looking at it. Refreshing it, or a play position arriving for a card, produces a new list, and
 * asking for focus again on that would yank the user back while they browse. A screen stacking
 * several rows leaves [claimsInitialFocus] false on all but the one the user should land on, so two
 * rows never ask at once.
 *
 * [initialFocusIndex] is the card that claim lands on, and [onItemFocused] reports where the focus
 * actually went. Together they let a screen put the user back on the card they left, rather than
 * always on the first one: returning to On Deck from a show page should land on the shelf and the
 * episode that were being browsed, not at the top of the page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlexPosterRow(
    itemCount: Int,
    modifier: Modifier = Modifier,
    claimsInitialFocus: Boolean = true,
    initialFocusIndex: Int = 0,
    key: (index: Int) -> String,
    onItemFocused: (index: Int) -> Unit = {},
    content: @Composable (index: Int, itemModifier: Modifier) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var focusRequested by remember { mutableStateOf(false) }
    // Clamped here rather than at each call site so a remembered card that has since fallen off the
    // end of a shorter list still resolves to a real card.
    val focusIndex = initialFocusIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))

    LaunchedEffect(claimsInitialFocus, itemCount > 0) {
        if (claimsInitialFocus && itemCount > 0 && !focusRequested) {
            focusRequested = true
            focusRequester.requestFocus()
        }
    }

    val leftEdgeSpec = rememberLeftEdgeSpec(leadingInset = FOCUSED_CARD_LEADING_INSET)
    // The glow tuning is read once per row rather than once per card: reading it where the card is
    // drawn ran two `getprop` processes for every poster the row composed.
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides leftEdgeSpec,
        LocalGlowTuning provides rememberGlowTuning()
    ) {
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
                val reportFocus = Modifier.onFocusChanged { if (it.isFocused) onItemFocused(index) }
                content(
                    index,
                    if (index == focusIndex) reportFocus.focusRequester(focusRequester) else reportFocus
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
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlexPosterCard(
    imageUrl: String,
    posterSize: PosterSize,
    modifier: Modifier = Modifier,
    onFocusChanged: (isFocused: Boolean) -> Unit = {},
    onPressed: () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    var posterFocused by remember(imageUrl) { mutableStateOf(false) }
    val posterRequest = rememberPosterRequest(imageUrl, posterSize.pixels)
    var posterLoaded by remember(imageUrl) { mutableStateOf(false) }
    val posterAlpha by animateFloatAsState(
        targetValue = if (posterLoaded) 1f else 0f,
        animationSpec = tween(POSTER_FADE_IN_MILLIS),
        label = "posterFadeIn"
    )
    val glowTuning = LocalGlowTuning.current
    // Animated rather than switched, so the card lights as it takes focus instead of snapping on,
    // and zero elevation costs nothing when the card is not focused.
    val glowElevation by animateDpAsState(
        targetValue = if (posterFocused && glowTuning.enabled) glowTuning.spread else 0.dp,
        animationSpec = tween(GLOW_FADE_MILLIS),
        label = "posterGlow"
    )
    val placeholder = MaterialTheme.colorScheme.surfaceVariant

    // The card is TV Material 3's own, so the focus treatment is the platform's rather than a
    // reimplementation: its Glow, its zoom and its press dip, the same indicators it draws behind
    // its own buttons and cards. It also clips to the shape, which is why nothing here needs a
    // graphics layer of its own any more.
    Card(
        onClick = onPressed,
        modifier = modifier
            .size(width = posterSize.height * POSTER_ASPECT_RATIO, height = posterSize.height)
            .onFocusChanged {
                posterFocused = it.isFocused
                onFocusChanged(it.isFocused)
            },
        shape = CardDefaults.shape(shape = POSTER_SHAPE),
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        scale = CardDefaults.scale(
            focusedScale = FOCUSED_POSTER_SCALE,
            pressedScale = PRESSED_POSTER_SCALE
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(FOCUS_BORDER_WIDTH, CARD_BORDER_COLOR),
                shape = POSTER_SHAPE
            )
        ),
        // A small glow that sits around the whole card, the way the Google TV home screen rings its
        // focused icons, rather than a shadow under it.
        glow = CardDefaults.glow(
            // Glow.None rather than a zero elevation, so a disabled glow draws nothing at all and
            // costs no animation. The card keeps the platform's border and zoom either way.
            focusedGlow = if (glowTuning.enabled) {
                Glow(
                    elevation = glowElevation,
                    elevationColor = Color.White.copy(alpha = glowTuning.alpha)
                )
            } else {
                Glow.None
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Painted under the poster so cards hold their space while Coil is still fetching.
                .drawBehind { drawRect(placeholder) },
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
val FOCUSED_POSTER_OVERHANG = 20.dp

/**
 * Where the focused card stops when the row scrolls it. The row's own 48.dp content padding only
 * positions the first and last cards, so the focused card needs a wider inset of its own to sit
 * clear of the screen edge.
 */
val FOCUSED_CARD_LEADING_INSET = 96.dp
val POSTER_ROW_BOTTOM_PADDING = 16.dp

/** Posters are sized relative to the screen so a shelf looks the same on any TV. */
const val POSTER_HEIGHT_FRACTION = 0.33f
val POSTER_CORNER_RADIUS = 12.dp
val POSTER_SHAPE = RoundedCornerShape(POSTER_CORNER_RADIUS)
const val FOCUSED_POSTER_SCALE = 1.06f

private const val HEADER_POSTER_HEIGHT_FRACTION = 0.48f
private val HEADER_POSTER_MAX_HEIGHT = 420.dp

/**
 * The glow TV Material 3 draws around a focused card: an even halo all the way round it, the way the
 * Google TV home screen rings a focused icon.
 *
 * Two numbers, and they do different things: [FOCUSED_POSTER_GLOW_ALPHA] is how bright it is, and
 * [FOCUSED_POSTER_GLOW_SPREAD] is how far it reaches. Raising the spread makes it wider and softer
 * rather than bigger and brighter. The spread also has to clear the card's own growth, a little over
 * 10dp at this card size, or the zoom covers the glow instead of sitting inside it.
 *
 * Both can be tried without a rebuild from a connected device, which is the quicker way to settle on
 * a pair: set the properties, then leave and re-enter the screen so the cards are composed again.
 *
 *     adb shell setprop debug.playerexp.glowAlpha 0.25
 *     adb shell setprop debug.playerexp.glowSpread 20
 *     adb shell setprop debug.playerexp.glowAlpha 0
 *
 * The zero clears the override and puts these constants back in charge.
 */
private val FOCUSED_POSTER_GLOW_ALPHA = 0.3f
private val FOCUSED_POSTER_GLOW_SPREAD = 16.dp
private const val GLOW_FADE_MILLIS = 180

/**
 * TEMPORARY: the glow is off while it is being tuned, so what the card is doing without it can be
 * compared against it. With it off the focused card keeps the platform's white border and its zoom and
 * simply has no halo. Turn it on here once the values above are settled, or at runtime with
 * `adb shell setprop debug.playerexp.glowOn 1`.
 */
private const val FOCUSED_POSTER_GLOW_ENABLED = false

const val PRESSED_POSTER_SCALE = 0.94f
const val PRESS_DOWN_MILLIS = 90
val FOCUS_BORDER_WIDTH = 3.dp
val CARD_BORDER_COLOR = Color.White.copy(alpha = 0.5f)

/** TEMPORARY: reads a debug property off the device so the glow can be tuned without a rebuild. */
private fun debugFloatProperty(name: String, fallback: Float): Float =
    runCatching {
        ProcessBuilder("getprop", name)
            .start()
            .inputStream.bufferedReader()
            .readText()
            .trim()
            .toFloat()
    }.getOrDefault(fallback)

/**
 * TEMPORARY: the glow's tuning, read once per row of cards rather than once per card.
 *
 * Reading the properties where the card is drawn cost two `getprop` process spawns on the
 * composition thread for every poster the row composed, which is tens of them across a shelf that
 * scrolls. The properties still apply per visit: a row recomposes from scratch when its screen is
 * re-entered, which is how they are meant to be tuned (`adb shell setprop`, then leave and come
 * back).
 */
@Composable
private fun rememberGlowTuning(): GlowTuning = remember {
    GlowTuning(
        enabled = FOCUSED_POSTER_GLOW_ENABLED &&
            debugFloatProperty("debug.playerexp.glowOn", 1f) > 0f,
        alpha = debugFloatProperty("debug.playerexp.glowAlpha", FOCUSED_POSTER_GLOW_ALPHA),
        spread = debugFloatProperty("debug.playerexp.glowSpread", FOCUSED_POSTER_GLOW_SPREAD.value).dp
    )
}

private data class GlowTuning(val enabled: Boolean, val alpha: Float, val spread: Dp)

/**
 * TEMPORARY: how the focused card's glow is tuned, shared by the cards of one row (see
 * [rememberGlowTuning]). The default is the compile-time constants with the glow off, so a card
 * drawn outside a row keeps the platform's border and zoom without spending any property reads.
 */
private val LocalGlowTuning = staticCompositionLocalOf {
    GlowTuning(
        enabled = false,
        alpha = FOCUSED_POSTER_GLOW_ALPHA,
        spread = FOCUSED_POSTER_GLOW_SPREAD
    )
}

/** How long a poster takes to fade in over its placeholder. */
const val POSTER_FADE_IN_MILLIS = 250
