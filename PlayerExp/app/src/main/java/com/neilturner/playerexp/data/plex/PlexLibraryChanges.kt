package com.neilturner.playerexp.data.plex

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide note that the library changed while nobody was looking.
 *
 * The On Deck ViewModel belongs to its navigation entry and is thrown away when the screen is
 * popped, so a flag held in the ViewModel would be forgotten by the time the user comes back. This
 * outlives any screen: the websocket marks it, the cache stops treating its copy as fresh, and the
 * next visit refetches straight away. The time-based staleness rule still applies on top, so a
 * socket that was never connected still lets the row go stale.
 */
object PlexLibraryChanges {

    private val _dirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _dirty.asStateFlow()

    fun markDirty() {
        _dirty.value = true
    }

    fun clear() {
        _dirty.value = false
    }
}
