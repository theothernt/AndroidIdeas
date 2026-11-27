package com.neilturner.visualmashup.ui.screens.simplecollage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.visualmashup.data.CollageLayouts
import com.neilturner.visualmashup.data.CollageMetrics
import com.neilturner.visualmashup.data.CollagePlacement
import com.neilturner.visualmashup.data.ImageOrientation
import com.neilturner.visualmashup.data.ImageSource
import com.neilturner.visualmashup.data.SourceImage
import com.neilturner.visualmashup.data.maxImageSize
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SimpleCollageUiState(
    val placement: CollagePlacement,
    val groupIndex: Int,
    val groupCount: Int,
)

/**
 * Holds the current group of images and the best full-bleed layout for them.
 *
 * A collage always uses at least two photos. While a group is on screen, the next group's images are
 * preloaded at the exact size each cell will render at, so advancing to it is a memory-cache hit.
 */
class SimpleCollageViewModel(application: Application) : AndroidViewModel(application) {

    private val imageLoader: ImageLoader = SingletonImageLoader.get(application)

    private val allImages = ImageSource.images
    private val landscapes = allImages.filter { it.orientation == ImageOrientation.LANDSCAPE }
    private val portraits = allImages.filter { it.orientation == ImageOrientation.PORTRAIT }
    private val squares = allImages.filter { it.orientation == ImageOrientation.SQUARE }

    private val groups: List<List<SourceImage>> = listOf(
        listOf(portraits[0], portraits[1]), // P, P
        listOf(landscapes[0], landscapes[1]), // L, L
        listOf(portraits[0], squares[0]), // P, S
        listOf(landscapes[0], portraits[0]), // L, P
        listOf(landscapes[0], portraits[0], squares[0]), // L, P, S
        listOf(portraits[0], portraits[1], portraits[2]), // P, P, P
        listOf(landscapes[0], landscapes[1], landscapes[2]), // L, L, L
        listOf(squares[0], squares[1]), // S, S
    )

    /** Each group's layout never changes, so it is solved once instead of per step and per preload. */
    private val placements: List<CollagePlacement> = groups.map { CollageLayouts.bestFor(it) }

    private val _uiState = MutableStateFlow(stateFor(0))
    val uiState: StateFlow<SimpleCollageUiState> = _uiState.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** True once the first placement's images have been decoded, so the screen can be shown. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        // Warm the next group while this one is on screen.
        preloadGroupsAhead(0, count = 1)
        viewModelScope.launch {
            requestsForGroup(0).forEach { imageLoader.execute(it) }
            _ready.value = true
        }
    }

    fun next() = step(1)

    fun previous() = step(-1)

    private fun step(delta: Int) {
        val nextIndex = (_uiState.value.groupIndex + delta + groups.size) % groups.size
        _uiState.value = stateFor(nextIndex)

        // Warm the next group while this one is on screen.
        preloadGroupsAhead(nextIndex, count = 1)
    }

    private fun stateFor(index: Int): SimpleCollageUiState {
        return SimpleCollageUiState(
            placement = placements[index],
            groupIndex = index,
            groupCount = groups.size,
        )
    }

    /**
     * Enqueues the next [count] groups at the pixel size each image will occupy, so the display
     * request is an exact memory-cache hit.
     */
    private fun preloadGroupsAhead(currentIndex: Int, count: Int) {
        for (ahead in 1..count) {
            val nextIndex = (currentIndex + ahead + groups.size) % groups.size
            requestsForGroup(nextIndex).forEach { imageLoader.enqueue(it) }
        }
    }

    /** The requests for every cell of [groupIndex], sized exactly as the screen draws them. */
    private fun requestsForGroup(groupIndex: Int): List<ImageRequest> {
        val app = getApplication<Application>()
        val metrics = app.resources.displayMetrics
        val gutterPx = (CollageMetrics.GUTTER_DP * metrics.density).roundToInt()
        val borderPx = (CollageMetrics.BORDER_WIDTH_DP * metrics.density).roundToInt()

        val placement = placements[groupIndex]

        return placement.images.mapIndexed { cellIndex, image ->
            val cell = placement.template.cells[cellIndex]
            val width = (cell.width * metrics.widthPixels - gutterPx - 2 * borderPx)
                .roundToInt()
                .coerceAtLeast(1)
            val height = (cell.height * metrics.heightPixels - gutterPx - 2 * borderPx)
                .roundToInt()
                .coerceAtLeast(1)

            ImageRequest.Builder(app)
                .data(image.url)
                .size(maxImageSize(width, height))
                .build()
        }
    }
}