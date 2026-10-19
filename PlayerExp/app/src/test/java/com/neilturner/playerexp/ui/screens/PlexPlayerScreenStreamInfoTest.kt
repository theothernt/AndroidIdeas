package com.neilturner.playerexp.ui.screens

import com.neilturner.playerexp.data.plex.PlexPlaybackStatus
import com.neilturner.playerexp.data.plex.PlexStreamMode
import com.neilturner.playerexp.data.plex.PlexStreamPlayback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlexPlayerScreenStreamInfoTest {

    @Test
    fun `a rate that is a whole number is written without a decimal`() {
        assertEquals("1x", formatPlaybackSpeed(1f))
        assertEquals("2x", formatPlaybackSpeed(2f))
    }

    @Test
    fun `a rate with a fraction keeps it`() {
        assertEquals("0.5x", formatPlaybackSpeed(0.5f))
        assertEquals("1.25x", formatPlaybackSpeed(1.25f))
    }

    @Test
    fun `a stream reads as its mode and its codec`() {
        val segment = streamSegment(PlexStreamPlayback(PlexStreamMode.Transcoded, "eac3"))

        assertEquals("Transcoded · EAC3", segment)
    }

    @Test
    fun `a stream with no codec reads as its mode alone`() {
        assertEquals("Direct", streamSegment(PlexStreamPlayback(PlexStreamMode.Direct)))
    }

    @Test
    fun `a stream that is not known yet reads as nothing`() {
        assertNull(streamSegment(null))
    }

    @Test
    fun `both streams direct read as direct`() {
        val status = PlexPlaybackStatus(
            video = PlexStreamPlayback(PlexStreamMode.Direct, "h264"),
            audio = PlexStreamPlayback(PlexStreamMode.Direct, "aac")
        )

        assertEquals("Direct", streamModeSummary(status))
    }

    @Test
    fun `only the audio being transcoded says so`() {
        val status = PlexPlaybackStatus(
            video = PlexStreamPlayback(PlexStreamMode.Direct, "h264"),
            audio = PlexStreamPlayback(PlexStreamMode.Transcoded, "eac3")
        )

        assertEquals("Audio Transcoded", streamModeSummary(status))
    }

    @Test
    fun `only the video being transcoded says so`() {
        val status = PlexPlaybackStatus(
            video = PlexStreamPlayback(PlexStreamMode.Transcoded, "h264"),
            audio = PlexStreamPlayback(PlexStreamMode.Direct, "aac")
        )

        assertEquals("Video Transcoded", streamModeSummary(status))
    }

    @Test
    fun `both streams transcoded read as transcoded`() {
        val status = PlexPlaybackStatus(
            video = PlexStreamPlayback(PlexStreamMode.Transcoded, "h264"),
            audio = PlexStreamPlayback(PlexStreamMode.Transcoded, "aac")
        )

        assertEquals("Transcoded", streamModeSummary(status))
    }

    @Test
    fun `a status that is not known yet reads as nothing`() {
        assertNull(streamModeSummary(null))
    }

    @Test
    fun `the resolution reads as the size of the picture`() {
        assertEquals("1920 × 1080", resolutionLabel(1920, 1080))
    }

    @Test
    fun `a picture that has not been decoded yet has no resolution`() {
        assertNull(resolutionLabel(null, null))
        assertNull(resolutionLabel(0, 1080))
    }
}
