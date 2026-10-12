package com.neilturner.playerexp.data.plex

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlexMediaMetadataTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The shape `/library/metadata/{ratingKey}` answers with, for a 4K 24p episode with EAC3. */
    private val metadataJson = """
        {
          "MediaContainer": {
            "size": 1,
            "Metadata": [
              {
                "ratingKey": "46751",
                "type": "episode",
                "title": "8:00 AM",
                "grandparentTitle": "Slow Horses",
                "viewOffset": 600000,
                "UserState": { "viewOffset": 600000, "viewCount": 1 },
                "Media": [
                  {
                    "id": 1,
                    "duration": 2700000,
                    "width": 3840,
                    "height": 2160,
                    "videoResolution": "uhd",
                    "videoFrameRate": "24p",
                    "Part": [
                      {
                        "id": 11,
                        "key": "/library/parts/11/file.mkv",
                        "Stream": [
                          {
                            "streamType": 1,
                            "codec": "hevc",
                            "width": 3840,
                            "height": 2160,
                            "frameRate": "23.976"
                          },
                          { "streamType": 2, "codec": "eac3", "channels": 6 }
                        ]
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `resolution and framerate are captured from the media element`() {
        val response = json.decodeFromString<PlexMediaContainerResponse>(metadataJson)

        val media = response.mediaContainer?.metadata?.firstOrNull()?.media?.firstOrNull()

        assertEquals(2_700_000L, media?.duration)
        assertEquals(3840, media?.width)
        assertEquals(2160, media?.height)
        assertEquals("uhd", media?.videoResolution)
        assertEquals("24p", media?.videoFrameRate)
    }

    @Test
    fun `the part still yields its key and its streams`() {
        val response = json.decodeFromString<PlexMediaContainerResponse>(metadataJson)

        val part = response.mediaContainer?.metadata?.firstOrNull()
            ?.media?.firstOrNull()?.part?.firstOrNull()
        val videoStream = part?.stream?.firstOrNull { it.streamType == 1 }
        val audioStream = part?.stream?.firstOrNull { it.streamType == 2 }

        assertEquals("/library/parts/11/file.mkv", part?.key)
        assertEquals("hevc", videoStream?.codec)
        assertEquals(3840, videoStream?.width)
        assertEquals(2160, videoStream?.height)
        assertEquals("23.976", videoStream?.frameRate)
        assertEquals("eac3", audioStream?.codec)
        assertNull(audioStream?.width)
    }

    @Test
    fun `the resume position is captured from the episode metadata`() {
        val response = json.decodeFromString<PlexMediaContainerResponse>(metadataJson)

        val metadata = response.mediaContainer?.metadata?.firstOrNull()

        assertEquals(600_000L, metadata?.viewOffset)
        assertEquals(600_000L, metadata?.userState?.viewOffset)
    }

    @Test
    fun `a response without the fields decodes them as null rather than failing`() {
        val legacyJson = """
            {"MediaContainer":{"Metadata":[{"ratingKey":"1","title":"Old",
            "Media":[{"Part":[{"id":1,"key":"/library/parts/1/file.avi"}]}]}]}}
        """.trimIndent()

        val response = json.decodeFromString<PlexMediaContainerResponse>(legacyJson)

        val media = response.mediaContainer?.metadata?.firstOrNull()?.media?.firstOrNull()

        assertNull(media?.videoResolution)
        assertNull(media?.videoFrameRate)
        assertNull(media?.width)
        assertNull(media?.part?.firstOrNull()?.stream)
        assertEquals("/library/parts/1/file.avi", media?.part?.firstOrNull()?.key)
    }
}
