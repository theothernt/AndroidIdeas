package com.neilturner.exifblur.slideshow

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SlideshowController(
    private val preloader: SlideshowPreloader,
    private val scope: CoroutineScope,
    private val displayDurationMs: Long = 10_000L,
    private val readyTimeoutMs: Long = 6_000L,
    private val fadeInDurationMs: Long = SLIDESHOW_TRANSITION_MS.toLong(),
) {
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isStarting = MutableStateFlow(true)
    val isStarting: StateFlow<Boolean> = _isStarting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null
    private var prefetchJob: Job? = null

    fun start() = startAt(0)

    fun skipNext() {
        if (preloader.size == 0) return
        val current = _currentIndex.value
        val from = if (current < 0) 0 else current
        startAt(wrap(from + 1, preloader.size))
    }

    fun stop() {
        job?.cancel()
        job = null
        prefetchJob?.cancel()
        prefetchJob = null
    }

    private fun startAt(index: Int) {
        job?.cancel()
        prefetchJob?.cancel()
        prefetchJob = null
        _isStarting.value = true
        _error.value = null
        job =
            scope.launch {
                if (preloader.size == 0) {
                    _error.value = "Playlist is empty"
                    _isStarting.value = false
                    return@launch
                }

                val first = if (preloader.awaitReady(index)) index else findFirstLoadable(index)
                if (first == null) {
                    _error.value = "No images in the playlist could be loaded"
                    _isStarting.value = false
                    return@launch
                }
                var shown: Int = first

                _currentIndex.value = shown
                _isStarting.value = false
                schedulePrefetch(shown)

                while (isActive) {
                    // The clock starts when the slide becomes visible; time spent
                    // waiting for the next slide extends this one.
                    delay(displayDurationMs)

                    val next = findNextReady(shown)
                    if (next == shown) continue
                    shown = next
                    _currentIndex.value = shown
                    schedulePrefetch(shown)
                }
            }
    }

    /**
     * Wait for the incoming slide's fade-in to finish before refilling the
     * prefetch window. Runs on its own job so the display timer is unaffected.
     */
    private fun schedulePrefetch(index: Int) {
        prefetchJob?.cancel()
        prefetchJob =
            scope.launch {
                delay(fadeInDurationMs)
                if (isActive) preloader.onCurrentIndexChanged(index)
            }
    }

    private suspend fun findNextReady(from: Int): Int {
        val count = preloader.size
        for (offset in 1..count) {
            val candidate = wrap(from + offset, count)
            val ready = withTimeoutOrNull(readyTimeoutMs) { preloader.awaitReady(candidate) }
            if (ready == true) return candidate
            if (ready == false) Log.w(TAG, "Skipping failed slide $candidate")
        }
        delay(RETRY_DELAY_MS)
        return from
    }

    private suspend fun findFirstLoadable(from: Int): Int? {
        val count = preloader.size
        for (offset in 0 until count) {
            val candidate = wrap(from + offset, count)
            if (withTimeoutOrNull(readyTimeoutMs) { preloader.load(candidate) } == true) {
                return candidate
            }
        }
        return null
    }

    private fun wrap(
        index: Int,
        size: Int,
    ): Int = ((index % size) + size) % size

    private companion object {
        const val TAG = "SlideshowController"
        const val RETRY_DELAY_MS = 500L
    }
}
