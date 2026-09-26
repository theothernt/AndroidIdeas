package com.neilturner.playerexp.ui.viewmodels

import com.neilturner.playerexp.data.plex.OnDeckItem

/**
 * On Deck outlives the screen that shows it.
 *
 * The Nav3 entry owns the ViewModel, so popping the screen and coming back builds a new one, which
 * used to mean a fresh Plex client and a fresh request every time. Holding the queue here lets the
 * screen paint immediately and only re-asks the server once the cached copy has had time to go
 * stale, which is as long as a Continue Watching row is worth trusting.
 */
object PlexOnDeckCache {
    private const val MAX_AGE_MILLIS = 60_000L

    private var items: List<OnDeckItem>? = null
    private var fetchedAtMillis = 0L

    /** The cached queue, or null when there is nothing to show or it has gone stale. */
    fun read(nowMillis: Long = System.currentTimeMillis()): List<OnDeckItem>? = synchronized(this) {
        items?.takeIf { nowMillis - fetchedAtMillis < MAX_AGE_MILLIS }
    }

    /** The cached queue whatever its age, so a stale refresh can still paint something first. */
    fun readStale(): List<OnDeckItem>? = synchronized(this) { items }

    fun write(items: List<OnDeckItem>, nowMillis: Long = System.currentTimeMillis()) = synchronized(this) {
        this.items = items
        fetchedAtMillis = nowMillis
    }

    fun clear() = synchronized(this) {
        items = null
        fetchedAtMillis = 0L
    }
}
