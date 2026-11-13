package com.neilturner.visualmashup.ui.screens.scrollingcollage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.neilturner.visualmashup.data.ImageSource
import com.neilturner.visualmashup.data.SourceImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

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

    private val cycle: List<SourceImage> = ImageSource.images.shuffled(Random(SEED))

    private val _items = MutableStateFlow(
        buildList {
            repeat(INITIAL_CYCLES) { addAll(cycle) }
        },
    )
    val items: StateFlow<List<SourceImage>> = _items.asStateFlow()

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
    }
}