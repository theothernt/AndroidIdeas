package com.neilturner.playerexp.ui.viewmodels

import androidx.compose.ui.unit.IntSize
import com.neilturner.playerexp.data.plex.OnDeckItem
import com.neilturner.playerexp.data.plex.PlexLibraryChanges

/**
 * On Deck outlives the screen that shows it.
 *
 * The Nav3 entry owns the ViewModel, so popping the screen and coming back builds a new one, which
 * used to mean a fresh Plex client and a fresh request every time. Holding the queue here lets the
 * screen paint immediately and only re-asks the server once the cached copy has had time to go
 * stale, which is as long as a Continue Watching row is worth trusting.
 *
 * Entries are keyed by the poster size they were built for, because the image URLs carry it: a
 * cached row served after the screen resolution changes would otherwise be drawn at the old size.
 *
 * A copy is also treated as stale as soon as the notification socket says the library changed, even
 * if it is younger than [MAX_AGE_MILLIS], so a row that is painted while the real copy is on its way
 * is visibly the older one rather than quietly presented as current. Freshness only decides how
 * quickly something appears on screen: a visit to the screen always asks the server again.
 */
object PlexOnDeckCache {
    private const val MAX_AGE_MILLIS = 60_000L

    private class Entry(
        val items: List<OnDeckItem>,
        val pixels: IntSize,
        val fetchedAtMillis: Long
    )

    private var entry: Entry? = null

    /**
     * The cached queue, or null when there is nothing to show or it has gone stale. A stale result is
     * not an error: the screen paints the old copy from [readStale] and then asks the server again.
     */
    fun read(pixels: IntSize, nowMillis: Long = System.currentTimeMillis()): List<OnDeckItem>? =
        synchronized(this) {
            if (PlexLibraryChanges.isDirty.value) {
                return@synchronized null
            }
            entry
                ?.takeIf { it.pixels == pixels && nowMillis - it.fetchedAtMillis < MAX_AGE_MILLIS }
                ?.items
        }

    /** The cached queue whatever its age, so a stale refresh can still paint something first. */
    fun readStale(pixels: IntSize): List<OnDeckItem>? = synchronized(this) {
        entry?.takeIf { it.pixels == pixels }?.items
    }

    fun write(
        items: List<OnDeckItem>,
        pixels: IntSize,
        nowMillis: Long = System.currentTimeMillis()
    ) = synchronized(this) {
        entry = Entry(items, pixels, nowMillis)
    }
}
