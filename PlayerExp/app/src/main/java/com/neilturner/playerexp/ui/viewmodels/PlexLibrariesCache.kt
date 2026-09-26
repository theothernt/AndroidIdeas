package com.neilturner.playerexp.ui.viewmodels

import androidx.compose.ui.unit.IntSize
import com.neilturner.playerexp.data.plex.PlexLibraryChanges
import com.neilturner.playerexp.data.plex.PlexLibraryShelf

/**
 * The library shelves outlive the screen that shows them, on the same terms as [PlexOnDeckCache]: a
 * visit paints whatever is here straight away and then asks the server, so returning to the screen is
 * instant without ever being the only source of the truth.
 */
object PlexLibrariesCache {
    private const val MAX_AGE_MILLIS = 60_000L

    private class Entry(
        val shelves: List<PlexLibraryShelf>,
        val pixels: IntSize,
        val fetchedAtMillis: Long
    )

    private var entry: Entry? = null

    fun read(pixels: IntSize, nowMillis: Long = System.currentTimeMillis()): List<PlexLibraryShelf>? =
        synchronized(this) {
            if (PlexLibraryChanges.isDirty.value) {
                return@synchronized null
            }
            entry
                ?.takeIf { it.pixels == pixels && nowMillis - it.fetchedAtMillis < MAX_AGE_MILLIS }
                ?.shelves
        }

    fun readStale(pixels: IntSize): List<PlexLibraryShelf>? = synchronized(this) {
        entry?.takeIf { it.pixels == pixels }?.shelves
    }

    fun write(
        shelves: List<PlexLibraryShelf>,
        pixels: IntSize,
        nowMillis: Long = System.currentTimeMillis()
    ) = synchronized(this) {
        entry = Entry(shelves, pixels, nowMillis)
    }
}
