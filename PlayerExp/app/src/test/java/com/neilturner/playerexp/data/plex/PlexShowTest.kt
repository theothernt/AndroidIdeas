package com.neilturner.playerexp.data.plex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexShowTest {

    @Test
    fun `groups episodes by season and orders season then episode`() {
        val episodes = listOf(
            ep("s2e1", season = 2, episode = 1),
            ep("s1e2", season = 1, episode = 2),
            ep("s1e1", season = 1, episode = 1),
            ep("s2e2", season = 2, episode = 2)
        )

        val seasons = groupEpisodesBySeason(episodes)

        assertEquals(listOf(1, 2), seasons.map { it.seasonNumber })
        assertEquals(listOf("s1e1", "s1e2"), seasons.first().episodes.map { it.ratingKey })
        assertEquals(listOf("s2e1", "s2e2"), seasons.last().episodes.map { it.ratingKey })
    }

    @Test
    fun `specials with no season sort last under their own heading`() {
        val episodes = listOf(
            ep("s1e1", season = 1, episode = 1),
            ep("special", season = null, episode = null),
            ep("s1e2", season = 1, episode = 2)
        )

        val seasons = groupEpisodesBySeason(episodes)

        assertEquals(listOf(1, null), seasons.map { it.seasonNumber })
        assertEquals("Specials", seasons.last().title)
        assertEquals(listOf("special"), seasons.last().episodes.map { it.ratingKey })
    }

    @Test
    fun `drops episodes with no rating key`() {
        val episodes = listOf(
            ep("", season = 1, episode = 1),
            ep("s1e1", season = 1, episode = 1)
        )

        val seasons = groupEpisodesBySeason(episodes)

        assertEquals(1, seasons.size)
        assertEquals(listOf("s1e1"), seasons.first().episodes.map { it.ratingKey })
    }

    @Test
    fun `progress fraction reads the play position and floors at zero`() {
        val watchedQuarter = ep("a", season = 1, episode = 1, duration = 4000L, viewOffset = 1000L)
        val unwatched = ep("b", season = 1, episode = 1, duration = 4000L, viewOffset = null)
        val noDuration = ep("c", season = 1, episode = 1, duration = null, viewOffset = 1000L)

        assertEquals(0.25f, watchedQuarter.progressFraction, 0.0001f)
        assertEquals(0f, unwatched.progressFraction, 0.0001f)
        assertEquals(0f, noDuration.progressFraction, 0.0001f)
        assertTrue(watchedQuarter.hasProgress)
        assertFalse(unwatched.hasProgress)
        assertFalse(noDuration.hasProgress)
    }

    @Test
    fun `progress fraction is clamped when the offset runs past the duration`() {
        val overplayed = ep("a", season = 1, episode = 1, duration = 1000L, viewOffset = 5000L)

        assertEquals(1f, overplayed.progressFraction, 0.0001f)
    }

    @Test
    fun `display title pairs the show and episode name`() {
        val episode = ep("a", season = 1, episode = 2, title = "The Heist")

        assertEquals("Banks — The Heist", episode.displayTitle("Banks"))
        assertEquals("The Heist", episode.displayTitle(null))
        assertEquals("Banks", ep("a", title = null).displayTitle("Banks"))
    }

    @Test
    fun `rating key is reduced from either shape plex reports`() {
        // Plex sends grandparentKey on a recently added episode as a full path, which doubled
        // into /library/metadata/library/metadata/{key} and came back as a 404 HTML page.
        assertEquals("40543", plexRatingKey("/library/metadata/40543"))
        assertEquals("40543", plexRatingKey("library/metadata/40543"))
        assertEquals("40543", plexRatingKey("40543"))
        assertEquals("40543", plexRatingKey("  40543  "))
    }

    @Test
    fun `rating key is null when plex reports nothing usable`() {
        assertEquals(null, plexRatingKey(null))
        assertEquals(null, plexRatingKey(""))
        assertEquals(null, plexRatingKey("   "))
        assertEquals(null, plexRatingKey("/library/metadata/"))
    }

    private fun ep(
        ratingKey: String,
        season: Int? = 1,
        episode: Int? = 1,
        title: String? = null,
        duration: Long? = null,
        viewOffset: Long? = null
    ) = PlexShowEpisode(
        ratingKey = ratingKey,
        title = title,
        seasonNumber = season,
        episodeNumber = episode,
        duration = duration,
        viewOffset = viewOffset
    )
}
