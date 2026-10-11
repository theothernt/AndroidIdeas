package com.neilturner.playerexp.ui.screens

import com.neilturner.playerexp.ui.viewmodels.PlexPlaybackMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlexPlayerScreenEpisodeLineTest {

    @Test
    fun `joins season and episode into the subtitle before the title`() {
        val line = overlayEpisodeLine(metadata("8:00 AM", season = 2, episode = 5))

        assertEquals("S2 · E5 — 8:00 AM", line)
    }

    @Test
    fun `keeps the title alone when neither number is present`() {
        val line = overlayEpisodeLine(metadata("Interstellar", episode = null, season = null))

        assertEquals("Interstellar", line)
    }

    @Test
    fun `drops the season for a special with only an episode number`() {
        val line = overlayEpisodeLine(metadata("Special", season = null, episode = 7))

        assertEquals("E7 — Special", line)
    }

    @Test
    fun `keeps the position when the item has no title of its own`() {
        val line = overlayEpisodeLine(metadata(title = "  ", season = 3, episode = 1))

        assertEquals("S3 · E1", line)
    }

    @Test
    fun `has no line at all when Plex named nothing`() {
        val line = overlayEpisodeLine(metadata(title = "", season = null, episode = null))

        assertNull(line)
    }

    private fun metadata(
        title: String?,
        season: Int?,
        episode: Int?
    ) = PlexPlaybackMetadata(
        showTitle = "Show",
        episodeTitle = title,
        seasonNumber = season,
        episodeNumber = episode
    )
}
