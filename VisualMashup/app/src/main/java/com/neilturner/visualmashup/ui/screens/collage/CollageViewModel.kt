package com.neilturner.visualmashup.ui.screens.collage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.neilturner.visualmashup.data.ImageSource
import com.neilturner.visualmashup.data.SourceImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val IMAGE_INTERVAL_MS = 5_000L
private const val PRELOAD_AHEAD = 2

/**
 * Drives the collage slideshow.
 *
 * The view model owns the image list, the current index and the 5 second timer, and warms the next
 * image in Coil's cache so each fade starts from a decoded frame. [CollageScreen] only renders the
 * state it exposes.
 */
class CollageViewModel(application: Application) : AndroidViewModel(application) {

    val images: List<SourceImage> = ImageSource.images

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val imageLoader: ImageLoader = SingletonImageLoader.get(application)

    init {
        preloadNext()
        viewModelScope.launch {
            while (true) {
                delay(IMAGE_INTERVAL_MS)
                _currentIndex.value = (_currentIndex.value + 1) % images.size
                preloadNext()
            }
        }
    }

    /** Fetches the image after the current one so it is cached before the next fade. */
    private fun preloadNext() {
        val app = getApplication<Application>()
        val metrics = app.resources.displayMetrics

        for (ahead in 1..PRELOAD_AHEAD) {
            val next = images[(_currentIndex.value + ahead) % images.size]
            imageLoader.enqueue(
                ImageRequest.Builder(app)
                    .data(next.url)
                    .size(Size(metrics.widthPixels, metrics.heightPixels))
                    .build(),
            )
        }
    }
}