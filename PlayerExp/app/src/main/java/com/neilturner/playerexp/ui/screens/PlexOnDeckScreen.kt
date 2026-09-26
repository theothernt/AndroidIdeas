package com.neilturner.playerexp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import kotlin.math.roundToInt
import com.neilturner.playerexp.R
import com.neilturner.playerexp.data.plex.OnDeckItem
import com.neilturner.playerexp.ui.modifiers.dpadSelectable
import com.neilturner.playerexp.ui.modifiers.rememberLeftEdgeSpec
import com.neilturner.playerexp.ui.viewmodels.PlexOnDeckUiState
import com.neilturner.playerexp.ui.viewmodels.PlexOnDeckViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlexOnDeckScreen(
    modifier: Modifier = Modifier,
    viewModel: PlexOnDeckViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val posterSize = rememberPosterSize()

    // Keyed on the size so a resolution change re-asks Plex for posters at the new size.
    LaunchedEffect(posterSize) {
        viewModel.loadOnDeck(posterSize.pixels)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.plex_on_deck_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = SCREEN_HORIZONTAL_PADDING)
        )
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // The row sits under the title rather than floating in the middle of the screen, so the
            // heading and the first poster share a top edge.
            contentAlignment = Alignment.TopCenter
        ) {
            when (val current = state) {
                is PlexOnDeckUiState.Loading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )

                is PlexOnDeckUiState.NotAuthorised -> StatusMessage(
                    text = stringResource(R.string.plex_on_deck_not_authorised)
                )

                is PlexOnDeckUiState.Error -> StatusMessage(text = current.message)

                is PlexOnDeckUiState.Success -> if (current.items.isEmpty()) {
                    StatusMessage(text = stringResource(R.string.plex_on_deck_empty))
                } else {
                    OnDeckRow(current.items, posterSize)
                }
            }
        }
    }
}

/**
 * The card size, kept as both the dp the layout draws at and the pixels that land on screen. Only
 * the pixel figure is meaningful to Plex and to Coil: handing layout a dp value derived from pixels
 * would scale it by the density a second time and draw the card at twice the size it fetched.
 */
private class PosterSize(val height: Dp, val pixels: IntSize)

@Composable
private fun rememberPosterSize(): PosterSize {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnDeckRow(items: List<OnDeckItem>, posterSize: PosterSize) {
    val firstPosterFocusRequester = remember { FocusRequester() }
    LaunchedEffect(items) {
        if (items.isNotEmpty()) {
            firstPosterFocusRequester.requestFocus()
        }
    }

    // The focused card stops at a fixed inset from the left edge as the row scrolls, instead of
    // stopping against whichever edge it happened to run out of room at.
    val leftEdgeSpec = rememberLeftEdgeSpec(leadingInset = FOCUSED_CARD_LEADING_INSET)
    CompositionLocalProvider(LocalBringIntoViewSpec provides leftEdgeSpec) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            // 48.dp lines the first poster up with the title; the vertical padding is headroom for
            // the focused card, which grows by 6% and would otherwise be clipped at the top.
            contentPadding = PaddingValues(
                start = SCREEN_HORIZONTAL_PADDING,
                end = SCREEN_HORIZONTAL_PADDING,
                top = FOCUSED_POSTER_OVERHANG,
                bottom = POSTER_ROW_BOTTOM_PADDING
            )
        ) {
            // Keyed by ratingKey so focus and the remembered request follow the item, not the index.
            itemsIndexed(items, key = { _, item -> item.ratingKey }) { index, item ->
                OnDeckCard(
                    item = item,
                    posterSize = posterSize,
                    modifier = if (index == 0) {
                        Modifier.focusRequester(firstPosterFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

/** Poster only: the image is the card, with the progress bar drawn on top of it. */
@Composable
private fun OnDeckCard(
    item: OnDeckItem,
    posterSize: PosterSize,
    modifier: Modifier = Modifier
) {
    var isFocused by remember(item.ratingKey) { mutableStateOf(false) }
    var selectCount by remember(item.ratingKey) { mutableIntStateOf(0) }
    val pressScale = remember { Animatable(1f) }
    val focusScale = remember { Animatable(1f) }
    val posterRequest = rememberPosterRequest(item.thumb, posterSize.pixels)
    val focusBorder = SolidColor(
        if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent
    )
    val placeholder = MaterialTheme.colorScheme.surfaceVariant

    // Focus grows the card and OK dips it, each on its own spring so neither has to wait for the
    // other. Only reading both values inside the layer keeps this to a redraw per frame instead of
    // a recomposition.
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
            .dpadSelectable { selectCount++ }
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
        if (item.thumb.isNotBlank()) {
            AsyncImage(
                model = posterRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        ProgressOverlay(
            fraction = item.progressFraction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = POSTER_BAR_INSET,
                    end = POSTER_BAR_INSET,
                    bottom = POSTER_BAR_INSET
                )
        )
    }
}

/**
 * Plex serves the poster at whatever size its thumb token points at, which is usually far larger
 * than the card. [PlexImageUrl] asks for a resized copy and states the size the card is drawn at,
 * so what arrives over the network and what Coil decodes are the same dimensions.
 */
@Composable
private fun rememberPosterRequest(url: String, posterSize: IntSize): ImageRequest {
    val context = LocalContext.current
    return remember(url, posterSize) {
        ImageRequest.Builder(context)
            .data(url)
            .size(CoilSize(posterSize.width, posterSize.height))
            .build()
    }
}

/**
 * Thin bar drawn inside the poster bounds, inset from the bottom and sides. Everything in this row
 * is something worth resuming, so the bar always draws. A position of a few seconds is too small a
 * sliver to read as progress, so the fill has a floor.
 */
@Composable
private fun ProgressOverlay(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(PROGRESS_BAR_HEIGHT)
            .clip(PROGRESS_BAR_SHAPE)
            .background(Color.White.copy(alpha = TRACK_ALPHA))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceAtLeast(MIN_PROGRESS_FRACTION))
                .fillMaxHeight()
                .clip(PROGRESS_BAR_SHAPE)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StatusMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 48.dp)
    )
}

private const val POSTER_ASPECT_RATIO = 2f / 3f

/** Shared left and right inset so the title and the first poster start on the same line. */
private val SCREEN_HORIZONTAL_PADDING = 48.dp

/** Room for the focused card to grow past its laid-out height without being clipped. */
private val FOCUSED_POSTER_OVERHANG = 12.dp

/**
 * Where the focused card stops when the row scrolls it. The row's own 48.dp content padding only
 * positions the first and last cards, so the focused card needs a wider inset of its own to sit
 * clear of the screen edge.
 */
private val FOCUSED_CARD_LEADING_INSET = 96.dp
private val POSTER_ROW_BOTTOM_PADDING = 8.dp

/** Posters are sized relative to the screen so the row looks the same on any TV. */
private const val POSTER_HEIGHT_FRACTION = 0.33f
private val POSTER_CORNER_RADIUS = 12.dp
private val POSTER_SHAPE = RoundedCornerShape(POSTER_CORNER_RADIUS)
private const val FOCUSED_POSTER_SCALE = 1.06f

/** Focus moves on a spring so a card grows and settles instead of snapping to size. */
private val FOCUS_SCALE_SPRING: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)
private const val PRESSED_POSTER_SCALE = 0.94f
private const val PRESS_DOWN_MILLIS = 90
private val FOCUS_BORDER_WIDTH = 3.dp
private val POSTER_BAR_INSET = 10.dp
private val PROGRESS_BAR_HEIGHT = 4.dp
private val PROGRESS_BAR_RADIUS = 2.dp
private val PROGRESS_BAR_SHAPE = RoundedCornerShape(PROGRESS_BAR_RADIUS)
private const val TRACK_ALPHA = 0.3f

/** Smallest fill the bar will draw, so a barely-started item still reads as in progress. */
private const val MIN_PROGRESS_FRACTION = 0.05f
