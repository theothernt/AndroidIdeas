package com.neilturner.visualmashup.ui.screens.collagepreview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
import coil3.size.Size
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

data class CollagePreviewUiState(
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
class CollagePreviewViewModel(application: Application) : AndroidViewModel(application) {

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

    private val _uiState = MutableStateFlow(stateFor(0))
    val uiState: StateFlow<CollagePreviewUiState> = _uiState.asStateFlow()

    fun next() = step(1)

    fun previous() = step(-1)

    private fun step(delta: Int) {
        _uiState.update {
            val nextIndex = (it.groupIndex + delta + groups.size) % groups.size
            stateFor(nextIndex)
        }
    }

    private fun stateFor(index: Int): CollagePreviewUiState {
        val images = groups[index]
        val placement = CollageLayouts.bestFor(images)

        // Warm the next group while this one is on screen.
        preloadGroupsAhead(index, count = 1)

        return CollagePreviewUiState(
            placement = placement,
            groupIndex = index,
            groupCount = groups.size,
        )
    }

    /**
     * Enqueues the next [count] groups at the pixel size each image will occupy, so the display
     * request is an exact memory-cache hit.
     */
    private fun preloadGroupsAhead(currentIndex: Int, count: Int) {
        val app = getApplication<Application>()
        val metrics = app.resources.displayMetrics
        val gutterPx = (CollageMetrics.GUTTER_DP * metrics.density).roundToInt()
        val borderPx = (CollageMetrics.BORDER_WIDTH_DP * metrics.density).roundToInt()

        for (ahead in 1..count) {
            val nextIndex = (currentIndex + ahead + groups.size) % groups.size
            val placement = CollageLayouts.bestFor(groups[nextIndex])

            placement.images.forEachIndexed { cellIndex, image ->
                val cell = placement.template.cells[cellIndex]
                val width = (cell.width * metrics.widthPixels - gutterPx - 2 * borderPx)
                    .roundToInt()
                    .coerceAtLeast(1)
                val height = (cell.height * metrics.heightPixels - gutterPx - 2 * borderPx)
                    .roundToInt()
                    .coerceAtLeast(1)

                imageLoader.enqueue(
                    ImageRequest.Builder(app)
                        .data(image.url)
                        .size(maxImageSize(width, height))
                        .build(),
                )
            }
        }
    }
}