package com.neilturner.playerexp.data.plex

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexSubtitleStreamTest {

    @Test
    fun `a subtitle in the media is not a file of its own`() {
        val stream = PlexSubtitleStream(id = 7, language = "English")

        assertFalse(stream.isExternal)
        assertNull(stream.key)
    }

    @Test
    fun `a subtitle with a key is a file beside the media`() {
        val stream = PlexSubtitleStream(id = 7, key = "/library/streams/264366")

        assertTrue(stream.isExternal)
    }

    @Test
    fun `each playable format is played as the media type for it`() {
        assertEquals(
            MimeTypes.APPLICATION_SUBRIP,
            PlexSubtitleStream(id = 1, format = "srt").mimeType
        )
        assertEquals(
            MimeTypes.TEXT_SSA,
            PlexSubtitleStream(id = 1, format = "ass").mimeType
        )
        assertEquals(
            MimeTypes.TEXT_VTT,
            PlexSubtitleStream(id = 1, format = "vtt").mimeType
        )
        assertEquals(
            MimeTypes.APPLICATION_TTML,
            PlexSubtitleStream(id = 1, format = "ttml").mimeType
        )
    }

    @Test
    fun `the format is used in preference to the codec`() {
        assertEquals(
            MimeTypes.APPLICATION_SUBRIP,
            PlexSubtitleStream(id = 1, codec = "srt", format = "srt").mimeType
        )
    }

    @Test
    fun `a format that cannot be played has no media type`() {
        assertNull(PlexSubtitleStream(id = 1, format = "pgs").mimeType)
        assertNull(PlexSubtitleStream(id = 1, format = "sub").mimeType)
        assertNull(PlexSubtitleStream(id = 1, format = "smi").mimeType)
        assertNull(PlexSubtitleStream(id = 1, format = "something else").mimeType)
    }

    @Test
    fun `a file of its own is fetched from the server`() {
        val stream = PlexSubtitleStream(
            id = 264366,
            language = "English",
            languageCode = "eng",
            title = "Signs",
            key = "/library/streams/264366",
            format = "srt"
        )

        assertEquals(
            "https://plex.example:32400/library/streams/264366",
            stream.externalSubtitleUrl("https://plex.example:32400")
        )
    }

    @Test
    fun `a file of its own at a full address is used as it stands`() {
        val stream = PlexSubtitleStream(
            id = 1,
            key = "https://elsewhere.example/subs.srt",
            format = "srt"
        )

        assertEquals(
            "https://elsewhere.example/subs.srt",
            stream.externalSubtitleUrl("https://plex.example:32400")
        )
    }

    @Test
    fun `a track inside the media has no file to fetch`() {
        val stream = PlexSubtitleStream(id = 7, language = "English", codec = "ass")

        assertNull(stream.externalSubtitleUrl("https://plex.example:32400"))
    }

    @Test
    fun `a file in a format that cannot be played is not fetched`() {
        val stream = PlexSubtitleStream(id = 7, key = "/library/streams/7", format = "pgs")

        assertNull(stream.externalSubtitleUrl("https://plex.example:32400"))
    }
}
