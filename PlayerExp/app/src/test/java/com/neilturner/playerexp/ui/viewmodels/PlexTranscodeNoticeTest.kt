package com.neilturner.playerexp.ui.viewmodels

import com.neilturner.playerexp.data.plex.PlexPlaybackStatus
import com.neilturner.playerexp.data.plex.PlexStreamMode
import com.neilturner.playerexp.data.plex.PlexStreamPlayback
import com.neilturner.playerexp.ui.screens.transcodeDetailLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexTranscodeNoticeTest {

    @Test
    fun `no notice when both streams direct play`() {
        assertNull(TranscodeNotice.fromPlaybackStatus(status(Direct, Direct)))
    }

    @Test
    fun `notice when only audio is transcoded`() {
        val notice = TranscodeNotice.fromPlaybackStatus(
            status(videoMode = Direct, audioMode = Transcoded, audioCodec = "eac3")
        )

        assertEquals(false, notice?.videoTranscoded)
        assertEquals(true, notice?.audioTranscoded)
        assertEquals("eac3", notice?.audio?.codec)
    }

    @Test
    fun `notice when only video is transcoded`() {
        val notice = TranscodeNotice.fromPlaybackStatus(
            status(videoMode = Transcoded, audioMode = Direct)
        )

        assertEquals(true, notice?.videoTranscoded)
        assertEquals(false, notice?.audioTranscoded)
    }

    @Test
    fun `notice when both streams are transcoded`() {
        val notice = TranscodeNotice.fromPlaybackStatus(
            status(videoMode = Transcoded, audioMode = Transcoded)
        )

        assertTrue(requireNotNull(notice).videoTranscoded)
        assertTrue(notice.audioTranscoded)
    }

    @Test
    fun `notice keeps a codecless stream rather than substituting one`() {
        val notice = TranscodeNotice.fromPlaybackStatus(
            status(videoMode = Transcoded, audioMode = Transcoded)
        )

        assertNull(requireNotNull(notice).video.codec)
        assertNull(notice.audio.codec)
    }

    @Test
    fun `detail line names each stream with its mode and codec`() {
        val notice = TranscodeNotice.fromPlaybackStatus(
            status(
                videoMode = Transcoded,
                audioMode = Direct,
                videoCodec = "h264",
                audioCodec = "aac"
            )
        )

        assertEquals("Video Transcoded · H264 · Audio Direct · AAC", transcodeDetailLine(notice!!))
    }

    @Test
    fun `detail line drops the codec Plex did not report`() {
        val notice = TranscodeNotice.fromPlaybackStatus(
            status(videoMode = Transcoded, audioMode = Direct)
        )

        assertEquals("Video Transcoded · Audio Direct", transcodeDetailLine(notice!!))
    }

    @Test
    fun `audio transcode reads as a direct video stream on the notice`() {
        val notice = TranscodeNotice.fromPlaybackStatus(
            status(
                videoMode = Direct,
                audioMode = Transcoded,
                videoCodec = "hevc",
                audioCodec = "eac3"
            )
        )

        assertFalse(requireNotNull(notice).videoTranscoded)
        assertEquals("Video Direct · HEVC · Audio Transcoded · EAC3", transcodeDetailLine(notice))
    }

    private fun status(
        videoMode: PlexStreamMode,
        audioMode: PlexStreamMode,
        videoCodec: String? = null,
        audioCodec: String? = null
    ) = PlexPlaybackStatus(
        video = PlexStreamPlayback(mode = videoMode, codec = videoCodec),
        audio = PlexStreamPlayback(mode = audioMode, codec = audioCodec)
    )

    private companion object {
        val Direct = PlexStreamMode.Direct
        val Transcoded = PlexStreamMode.Transcoded
    }
}
