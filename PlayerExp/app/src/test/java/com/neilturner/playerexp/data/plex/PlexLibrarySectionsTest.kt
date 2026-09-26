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

    private fun section(key: String, title: String, type: String) =
        PlexSectionDirectory(key = key, type = type, title = title)
}
