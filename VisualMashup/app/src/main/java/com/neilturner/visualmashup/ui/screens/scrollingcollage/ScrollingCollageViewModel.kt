package com.neilturner.visualmashup.ui.screens.scrollingcollage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.neilturner.visualmashup.data.CollageMetrics
import com.neilturner.visualmashup.data.ImageSource
import com.neilturner.visualmashup.data.ScrollingCollageLayout
import com.neilturner.visualmashup.data.SourceImage
import com.neilturner.visualmashup.data.maxImageSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.math.roundToInt

/** Extra items kept ahead of the viewport so the grid never runs out while scrolling. */
private const val AHEAD_ITEMS = 30

/**
 * Supplies an endless list of images for the scrolling collage.
 *
 * The images are shown in a fixed, deterministic shuffle so the columns mix orientations instead of
 * showing one orientation per screen. The list grows by one full cycle whenever the viewport gets
 * near the end, so the collage keeps loading more images and repeats after every [ImageSource.COUNT].
 */
class ScrollingCollageViewModel(application: Application) : AndroidViewModel(application) {

    private val imageLoader: ImageLoader = SingletonImageLoader.get(application)

    private val cycle: List<SourceImage> = ImageSource.images.shuffled(Random(SEED))

    private val _items = MutableStateFlow(
        buildList {
            repeat(INITIAL_CYCLES) { addAll(cycle) }
        },
    )
    val items: StateFlow<List<SourceImage>> = _items.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** True once the first rows of images have been decoded, so the screen can be shown. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            warmInitialImages()
            _ready.value = true
        }
    }

    /**
     * Decodes the images that make up the first screen at the exact tile size the grid requests, so
     * the initial render is a memory-cache hit instead of a burst of network loads.
     */
    private suspend fun warmInitialImages() {
        val app = getApplication<Application>()
        val metrics = app.resources.displayMetrics
        val density = metrics.density

        val maxWidthDp = metrics.widthPixels / density
        val columnWidthDp = (maxWidthDp - CollageMetrics.GUTTER_DP * (ScrollingCollageLayout.COLUMNS + 1)) /
            ScrollingCollageLayout.COLUMNS
        val imageWidthPx = ((columnWidthDp - CollageMetrics.BORDER_WIDTH_DP * 2) * density)
            .roundToInt()
            .coerceAtLeast(1)

        for (image in cycle.take(INITIAL_READY_IMAGES)) {
            val aspect = ScrollingCollageLayout.tileAspect(image)
            val imageHeightPx = (imageWidthPx * aspect).roundToInt().coerceAtLeast(1)

            imageLoader.execute(
                ImageRequest.Builder(app)
                    .data(image.url)
                    .size(maxImageSize(imageWidthPx, imageHeightPx))
                    .build(),
            )
        }
    }

    /** Appends another copy of the cycle when [firstVisibleIndex] nears the end of the list. */
    fun ensureItems(firstVisibleIndex: Int) {
        if (firstVisibleIndex + AHEAD_ITEMS <= _items.value.size) return

        _items.update { current ->
            if (firstVisibleIndex + AHEAD_ITEMS <= current.size) current else current + cycle
        }
    }

    private companion object {
        const val SEED = 7
        const val INITIAL_CYCLES = 2

        /** Enough images to cover the first staggered rows on a TV. */
        const val INITIAL_READY_IMAGES = 9
    }
}