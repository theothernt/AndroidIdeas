package com.neilturner.playerexp.data.plex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexLibrarySectionsTest {

    /** The libraries as this server actually reports them, alternates and all. */
    private val serverSections = listOf(
        section("7", "Alt. Movies", "movie"),
        section("1", "Films", "movie"),
        section("8", "Alt. TV", "show"),
        section("4", "TV", "show"),
        section("9", "Music", "artist"),
        section("10", "Sports", "movie")
    )

    @Test
    fun `picks the canonical library and skips the alternates`() {
        assertEquals("4", PlexLibrarySections.pickTvShows(serverSections)?.key)
        assertEquals("1", PlexLibrarySections.pickMovies(serverSections)?.key)
    }

    @Test
    fun `ignores libraries of another type entirely`() {
        assertNull(PlexLibrarySections.pickTvShows(serverSections.filter { it.type == "artist" }))
        assertNull(PlexLibrarySections.pickMovies(listOf(section("9", "Music", "artist"))))
    }

    @Test
    fun `a canonical name wins over a differently named library of the same type`() {
        val sections = listOf(
            section("10", "Sports", "movie"),
            section("1", "Films", "movie")
        )

        assertEquals("1", PlexLibrarySections.pickMovies(sections)?.key)
    }

    @Test
    fun `falls back to the server order when no library carries a canonical name`() {
        val sections = listOf(
            section("10", "Sports", "movie"),
            section("11", "Documentaries", "movie")
        )

        assertEquals("10", PlexLibrarySections.pickMovies(sections)?.key)
    }

    @Test
    fun `recognises the ways a library announces itself as a stand-in`() {
        assertTrue(PlexLibrarySections.looksAlternate("Alt. TV"))
        assertTrue(PlexLibrarySections.looksAlternate("alt movies"))
        assertTrue(PlexLibrarySections.looksAlternate("TV (Alternate)"))
        assertTrue(PlexLibrarySections.looksAlternate("Movies alt"))
        assertTrue(PlexLibrarySections.looksAlternate("Test Shows"))

        assertFalse(PlexLibrarySections.looksAlternate("TV Shows"))
        assertFalse(PlexLibrarySections.looksAlternate("Films"))
        // A title that merely contains the letters, not the word.
        assertFalse(PlexLibrarySections.looksAlternate("Alternative Rock"))
    }

    @Test
    fun `a section with no key is never picked`() {
        val sections = listOf(
            section("", "TV Shows", "show"),
            section("4", "Archive", "show")
        )

        assertEquals("4", PlexLibrarySections.pickTvShows(sections)?.key)
    }

    @Test
    fun `matches the type whatever case the server uses`() {
        val sections = listOf(section("4", "TV", "Show"))

        assertEquals("4", PlexLibrarySections.pickTvShows(sections)?.key)
    }

    @Test
    fun `orders a library by most recently added`() {
        val items = listOf(
            item("1", "Older", addedAt = 1_000L),
            item("2", "Newest", addedAt = 3_000L),
            item("3", "Middle", addedAt = 2_000L)
        )

        val ordered = PlexLibrarySections.orderByRecentlyAdded(items)

        assertEquals(listOf("Newest", "Middle", "Older"), ordered.map { it.title })
    }

    @Test
    fun `items with no date keep the server order at the end of the shelf`() {
        val items = listOf(
            item("1", "No date one", addedAt = null),
            item("2", "Dated", addedAt = 500L),
            item("3", "No date two", addedAt = null)
        )

        val ordered = PlexLibrarySections.orderByRecentlyAdded(items)

        assertEquals(listOf("Dated", "No date one", "No date two"), ordered.map { it.title })
    }

    private fun item(ratingKey: String, title: String, addedAt: Long?) =
        PlexLibraryItem(
            ratingKey = ratingKey,
            title = title,
            thumb = "/library/metadata/$ratingKey/thumb/1",
            addedAt = addedAt
        )

    private fun section(key: String, title: String, type: String) =
        PlexSectionDirectory(key = key, type = type, title = title)
}
