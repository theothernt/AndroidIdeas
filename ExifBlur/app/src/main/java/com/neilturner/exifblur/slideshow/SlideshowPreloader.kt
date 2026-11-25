package com.neilturner.exifblur.slideshow

import android.content.Context
import android.util.Log
import android.util.Size
import coil3.ImageLoader
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext

/**
 * Plain Kotlin prefetcher. Holds only Deferred<Boolean> per index — true means
 * decoded and in Coil's memory cache, false means the load failed. Decoded
 * images are deliberately not retained here.
 */
class SlideshowPreloader(
    private val imageLoader: ImageLoader,
    private val scope: CoroutineScope,
    private val context: Context,
    private val targetSize: Size,
    private val windowSize: Int = 2,
    private val maxConcurrent: Int = 1,
    private val keepPrevious: Boolean = false,
) {
    private val lock = Any()
    private val jobs = mutableMapOf<Int, Deferred<Boolean>>()
    private val permits = Semaphore(maxConcurrent.coerceAtLeast(1))
    private var items: List<Any> = emptyList()
    private var currentIndex: Int = 0

    private val _isPrefetching = MutableStateFlow(false)
    /** True while any image in the prefetch window is still fetching/decoding. */
    val isPrefetching: StateFlow<Boolean> = _isPrefetching.asStateFlow()

    val size: Int get() = items.size

    fun itemAt(index: Int): Any? = if (items.isEmpty()) null else items[wrap(index, items.size)]

    fun setPlaylist(newItems: List<Any>) {
        cancelAll()
        synchronized(lock) {
            items = newItems
            currentIndex = 0
        }
    }

    /** Keep [current .. current + windowSize], wrapping for looping playlists. */
    fun onCurrentIndexChanged(index: Int) {
        if (items.isEmpty()) return
        val count = items.size
        val normalized = wrap(index, count)
        synchronized(lock) { currentIndex = normalized }

        for (offset in 1..windowSize.coerceAtLeast(0)) {
            getOrCreate(normalized + offset)
        }

        // Cancel and forget anything that fell out of the window. In RAM-only
        // mode the previous index is kept too (current + next + previous).
        val previous = if (keepPrevious) wrap(normalized - 1, count) else -1
        synchronized(lock) {
            val iterator = jobs.iterator()
            while (iterator.hasNext()) {
                val (idx, deferred) = iterator.next()
                val inWindow = distance(idx, normalized, count) in 0..windowSize
                if (!inWindow && idx != previous) {
                    deferred.cancel()
                    iterator.remove()
                }
            }
        }
        refreshPrefetching()
    }

    /** Explicitly load one index; used for the first slide. */
    suspend fun load(index: Int): Boolean {
        getOrCreate(index) ?: return false
        return awaitReady(index)
    }

    /** Suspends until the index is decoded, has failed, or its job was pruned. */
    suspend fun awaitReady(index: Int): Boolean {
        if (items.isEmpty()) return false
        val deferred = synchronized(lock) { jobs[wrap(index, items.size)] } ?: return false
        return try {
            deferred.await()
        } catch (e: CancellationException) {
            // The job left the window. Don't swallow cancellation of the caller.
            if (!coroutineContext.isActive) throw e
            false
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun isReady(index: Int): Boolean {
        if (items.isEmpty()) return false
        val deferred = synchronized(lock) { jobs[wrap(index, items.size)] } ?: return false
        if (!deferred.isCompleted || deferred.isCancelled) return false
        return try {
            deferred.getCompleted()
        } catch (_: Throwable) {
            false
        }
    }

    fun close() {
        cancelAll()
        synchronized(lock) { items = emptyList() }
    }

    private fun cancelAll() {
        synchronized(lock) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
        refreshPrefetching()
    }

    private fun refreshPrefetching() {
        _isPrefetching.value = synchronized(lock) { jobs.values.any { !it.isCompleted } }
    }

    private fun getOrCreate(index: Int): Deferred<Boolean>? {
        if (items.isEmpty()) return null
        val count = items.size
        val normalized = wrap(index, count)
        val deferred = synchronized(lock) {
            jobs[normalized] ?: run {
                val model = items[normalized]
                val newDeferred =
                    scope.async {
                        permits.withPermit {
                            try {
                                val result =
                                    imageLoader.execute(
                                        buildSlideshowRequest(context, model, targetSize),
                                    )
                                when (result) {
                                    is SuccessResult -> {
                                        // result.image is a coil3.Image; not kept here.
                                        Log.d(
                                            TAG,
                                            "prefetch[$normalized] ok source=${result.dataSource} " +
                                                "sampled=${result.isSampled}",
                                        )
                                        true
                                    }

                                    is ErrorResult -> {
                                        Log.w(
                                            TAG,
                                            "prefetch[$normalized] failed: ${result.throwable.message}",
                                            result.throwable,
                                        )
                                        false
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (t: Throwable) {
                                Log.w(TAG, "prefetch[$normalized] crashed", t)
                                false
                            }
                        }
                    }
                jobs[normalized] = newDeferred
                newDeferred.invokeOnCompletion { refreshPrefetching() }
                newDeferred
            }
        }
        refreshPrefetching()
        return deferred
    }

    private fun wrap(
        index: Int,
        size: Int,
    ): Int = ((index % size) + size) % size

    private fun distance(
        index: Int,
        from: Int,
        size: Int,
    ): Int = wrap(index - from, size)

    private companion object {
        const val TAG = "SlideshowPreloader"
    }
}
