package com.neilturner.visualmashup.ui.screens.effect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.neilturner.visualmashup.data.ImageSource
import com.neilturner.visualmashup.data.SourceImage
import com.neilturner.visualmashup.data.maxImageSize
import com.neilturner.visualmashup.ui.effects.EffectSettings
import com.neilturner.visualmashup.ui.effects.EffectSpeed
import com.neilturner.visualmashup.ui.effects.ImageEffect
import com.neilturner.visualmashup.ui.effects.PixelationCache
import com.neilturner.visualmashup.ui.effects.Transition
import com.neilturner.visualmashup.ui.effects.TransitionSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val EFFECT_IMAGE_INTERVAL_MS = 5_000L
private const val PRELOAD_AHEAD = 2

/**
 * Drives the Effects slideshow: one image at a time, five seconds each, with the next images
 * preloaded at the full display size so transitions are memory-cache hits.
 */
class EffectViewModel(application: Application) : AndroidViewModel(application) {

    val images: List<SourceImage> = ImageSource.images
    val effect: StateFlow<ImageEffect> = EffectSettings.effect
    val speed: StateFlow<EffectSpeed> = EffectSettings.speed
    val transition: StateFlow<Transition> = TransitionSettings.transition

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val imageLoader: ImageLoader = SingletonImageLoader.get(application)

    init {
        preloadNext()
        // Warm the pixelation levels while the Pixelate transition is selected, so the
        // decode/downscale doesn't happen during the transition.
        viewModelScope.launch {
            TransitionSettings.transition.collect { transition ->
                if (transition == Transition.PIXELATE) preloadPixelation()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(EFFECT_IMAGE_INTERVAL_MS)
                _currentIndex.value = (_currentIndex.value + 1) % images.size
                preloadNext()
                if (TransitionSettings.transition.value == Transition.PIXELATE) preloadPixelation()
            }
        }
    }

    private fun preloadPixelation() {
        val app = getApplication<Application>()
        val metrics = app.resources.displayMetrics
        viewModelScope.launch {
            for (ahead in 0..1) {
                val image = images[(_currentIndex.value + ahead) % images.size]
                PixelationCache.getLevels(app, image, metrics.widthPixels, metrics.heightPixels)
            }
        }
    }

    private fun preloadNext() {
        val app = getApplication<Application>()
        val metrics = app.resources.displayMetrics

        for (ahead in 1..PRELOAD_AHEAD) {
            val next = images[(_currentIndex.value + ahead) % images.size]
            imageLoader.enqueue(
                ImageRequest.Builder(app)
                    .data(next.url)
                    .size(maxImageSize(metrics.widthPixels, metrics.heightPixels))
                    .build(),
            )
        }
    }
}