package com.neilturner.playerexp.ui.screens

import com.neilturner.playerexp.data.plex.PlexShow
import com.neilturner.playerexp.data.plex.PlexShowEpisode
import com.neilturner.playerexp.data.plex.PlexShowSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlexShowScreenIndexTest {

    @Test
    fun `finds an episode by rating key across seasons`() {
        val show = showWith(
            season(1, listOf(ep("s1e1", 1), ep("s1e2", 2))),
            season(2, listOf(ep("s2e1", 1), ep("s2e2", 2))),
            season(3, listOf(ep("s3e33", 33)))
        )

        val index = findEpisodeIndex(show, "s3e33")

        assertEquals(Pair(2, 0), index)
    }

    @Test
    fun `index points at the right episode within its season`() {
        val show = showWith(
            season(1, listOf(ep("s1e1", 1))),
            season(2, listOf(ep("s2e1", 1), ep("s2e2", 2), ep("s2e3", 3)))
        )

        val (seasonIndex, episodeIndex) = findEpisodeIndex(show, "s2e3")!!

        assertEquals(1, seasonIndex)
        assertEquals(2, episodeIndex)
    }

    @Test
    fun `returns null when the episode is not in the show`() {
        val show = showWith(
            season(1, listOf(ep("s1e1", 1), ep("s1e2", 2)))
        )

        assertNull(findEpisodeIndex(show, "not-here"))
    }

    private fun showWith(vararg seasons: PlexShowSeason) = PlexShow(
        ratingKey = "show",
        title = "Show",
        summary = null,
        year = null,
        posterUrl = null,
        seasonCount = seasons.size,
        episodeCount = seasons.sumOf { it.episodes.size },
        seasons = seasons.toList()
    )

    private fun season(number: Int, episodes: List<PlexShowEpisode>) =
        PlexShowSeason(seasonNumber = number, episodes = episodes, plexTitle = null)

    private fun ep(ratingKey: String, number: Int) = PlexShowEpisode(
        ratingKey = ratingKey,
        title = ratingKey,
        seasonNumber = null,
        episodeNumber = number,
        duration = null,
        viewOffset = null
    )
}
