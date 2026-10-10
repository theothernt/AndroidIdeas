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

    @Test
    fun `show details parse the title art beside the poster and backdrop`() {
        // Plex reports the clearLogo on the same metadata element as the poster and backdrop art,
        // and a show the agent kept no logo for simply has no such field.
        val json = """
            {
              "MediaContainer": {
                "Metadata": [
                  {
                    "ratingKey": "40543",
                    "title": "Severance",
                    "thumb": "/library/metadata/40543/thumb/1",
                    "art": "/library/metadata/40543/art/2",
                    "clearLogo": "/library/metadata/40543/clearLogo/3"
                  }
                ]
              }
            }
        """.trimIndent()

        val metadata = plexJson
            .decodeFromString(PlexShowDetailsResponse.serializer(), json)
            .mediaContainer?.metadata?.first()

        assertEquals("Severance", metadata?.title)
        assertEquals("/library/metadata/40543/thumb/1", metadata?.thumb)
        assertEquals("/library/metadata/40543/art/2", metadata?.art)
        assertEquals("/library/metadata/40543/clearLogo/3", metadata?.clearLogo)
    }

    @Test
    fun `clear logo parse keeps every logo on the listing`() {
        // The shape /library/metadata/{key}/clearLogos answers with: absolute provider URLs, and
        // the one Plex picked as a local file path, with the selected one flagged.
        val json = """
            {
              "MediaContainer": {
                "size": 2,
                "Metadata": [
                  {
                    "key": "https://metadata-static.plex.tv/d/1/aaa.png",
                    "ratingKey": "https://metadata-static.plex.tv/d/1/aaa.png",
                    "selected": false,
                    "provider": "tmdb"
                  },
                  {
                    "key": "/library/metadata/40941/file?url=metadata%3A%2F%2FclearLogos%2Ftv.plex.agents.series_27f5",
                    "thumb": "/library/metadata/40941/file?url=metadata%3A%2F%2FclearLogos%2Ftv.plex.agents.series_27f5",
                    "selected": true,
                    "provider": "tmdb"
                  }
                ]
              }
            }
        """.trimIndent()

        val photos = plexJson
            .decodeFromString(PlexClearLogosResponse.serializer(), json)
            .mediaContainer?.metadata.orEmpty()

        assertEquals(2, photos.size)
        assertEquals(
            "/library/metadata/40941/file?url=metadata%3A%2F%2FclearLogos%2Ftv.plex.agents.series_27f5",
            clearLogoImagePath(chosenClearLogo(photos))
        )
    }

    @Test
    fun `clear logo picks the selected art and falls back to the first offered`() {
        val selected = listOf(
            PlexClearLogoPhoto(key = "http://one.png", selected = false),
            PlexClearLogoPhoto(key = "http://two.png", selected = true)
        )
        val noneSelected = listOf(
            PlexClearLogoPhoto(key = "http://one.png", selected = false),
            PlexClearLogoPhoto(key = "http://two.png", selected = null)
        )

        assertEquals("http://two.png", clearLogoImagePath(chosenClearLogo(selected)))
        assertEquals("http://one.png", clearLogoImagePath(chosenClearLogo(noneSelected)))
    }

    @Test
    fun `clear logo image is null for a show with no usable logo`() {
        assertEquals(null, clearLogoImagePath(chosenClearLogo(emptyList())))

        // A chosen logo that carries neither artwork of its own nor a resized copy.
        val blank = listOf(PlexClearLogoPhoto(key = "", thumb = "   ", selected = true))
        assertEquals(null, clearLogoImagePath(chosenClearLogo(blank)))

        // One with no key of its own falls back to the server-resized copy.
        val keyless = listOf(PlexClearLogoPhoto(thumb = "http://resized.png", selected = true))
        assertEquals("http://resized.png", clearLogoImagePath(chosenClearLogo(keyless)))
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
