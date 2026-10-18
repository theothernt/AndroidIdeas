package com.neilturner.playerexp.ui.screens

import com.neilturner.playerexp.data.plex.PlexAudioStream
import com.neilturner.playerexp.data.plex.PlexSubtitleStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexPlayerTrackChoicesTest {

    @Test
    fun `audio choices carry every track and mark the selected one`() {
        val choices = audioChoices(
            audio = listOf(
                PlexAudioStream(id = 1, language = "English", codec = "eac3", channels = 6),
                PlexAudioStream(id = 2, language = "Commentary", codec = "aac", channels = 2)
            ),
            selectedAudioId = 2
        )

        assertEquals(listOf("English (EAC3 · 6ch)", "Commentary (AAC · 2ch)"), choices.map { it.label })
        assertEquals(listOf(false, true), choices.map { it.selected })
        assertEquals(listOf(1L, 2L), choices.map { it.streamId })
    }

    @Test
    fun `audio choices are empty when the item reported no tracks`() {
        assertTrue(audioChoices(audio = emptyList(), selectedAudioId = null).isEmpty())
    }

    @Test
    fun `an unknown audio selection leaves every chip unselected`() {
        val choices = audioChoices(
            audio = listOf(PlexAudioStream(id = 1, language = "English")),
            selectedAudioId = 99
        )

        assertFalse(choices.single().selected)
    }

    @Test
    fun `subtitle choices put Off first and it is selected when nothing else is`() {
        val choices = subtitleChoices(
            subtitles = listOf(PlexSubtitleStream(id = 7, language = "English", codec = "srt")),
            selectedSubtitleId = null,
            offLabel = "Off"
        )

        assertEquals(listOf("Off", "English (SRT)"), choices.map { it.label })
        assertTrue(choices.first().selected)
        assertNull(choices.first().streamId)
    }

    @Test
    fun `subtitle choices mark the chosen track and clear Off`() {
        val choices = subtitleChoices(
            subtitles = listOf(
                PlexSubtitleStream(id = 7, language = "English", codec = "srt"),
                PlexSubtitleStream(id = 8, language = "Spanish", codec = "srt")
            ),
            selectedSubtitleId = 8,
            offLabel = "Off"
        )

        assertEquals(listOf(false, false, true), choices.map { it.selected })
        assertEquals(8L, choices.last().streamId)
    }

    @Test
    fun `subtitle choices are just Off when the item carries none`() {
        val choices = subtitleChoices(subtitles = emptyList(), selectedSubtitleId = null, offLabel = "Off")

        assertEquals(1, choices.size)
        assertEquals("Off", choices.single().label)
        assertTrue(choices.single().selected)
    }

    @Test
    fun `every choice has its own key so the picker row never reuses a chip`() {
        val choices = subtitleChoices(
            subtitles = listOf(PlexSubtitleStream(id = 7), PlexSubtitleStream(id = 8)),
            selectedSubtitleId = 7,
            offLabel = "Off"
        )

        assertEquals(choices.size, choices.map { it.key }.toSet().size)
    }
}
