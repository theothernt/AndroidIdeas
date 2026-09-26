package com.neilturner.playerexp.data.plex

import io.ktor.http.URLBuilder
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The resize request is the whole point of asking Plex for small posters, and the part most likely
 * to break quietly is the encoding: the `:/` in the transcode path and the nested thumb path in the
 * `url` parameter both have to survive a round trip, because a malformed URL fails as a blank card
 * rather than as an error.
 */
class PlexImageUrlTest {

    private val serverUrl = "http://192.168.1.5:32400"
    private val token = "TOKEN123"
    private val thumbPath = "/library/metadata/12345/thumb/1697123456"

    @Test
    fun `keeps the transcode path unencoded`() {
        val url = PlexImageUrl.build(serverUrl, token, thumbPath, 432, 648)

        assertTrue(url, url.contains("/photo/:/transcode?"))
        assertFalse(url, url.contains("%3A"))
    }

    @Test
    fun `asks for the size the card draws at`() {
        val builder = URLBuilder(PlexImageUrl.build(serverUrl, token, thumbPath, 432, 648))

        assertEquals("432", builder.parameters["width"])
        assertEquals("648", builder.parameters["height"])
        assertEquals("1", builder.parameters["minSize"])
        assertEquals("1", builder.parameters["upscale"])
    }

    @Test
    fun `carries the original thumb path and token`() {
        val builder = URLBuilder(PlexImageUrl.build(serverUrl, token, thumbPath, 432, 648))

        assertEquals(thumbPath, builder.parameters[PlexImageUrl.ORIGINAL_PATH_PARAM])
        assertEquals(token, builder.parameters[PlexImageUrl.TOKEN_PARAM])
    }

    @Test
    fun `targets the server the same however it is written`() {
        val withSlash = PlexImageUrl.build("$serverUrl/", token, thumbPath, 432, 648)

        assertEquals(PlexImageUrl.build(serverUrl, token, thumbPath, 432, 648), withSlash)
    }

    @Test
    fun `is a url OkHttp can load, which is how Coil reads it`() {
        val url = PlexImageUrl.build(serverUrl, token, thumbPath, 432, 648).toHttpUrl()

        assertEquals(PlexImageUrl.TRANSCODE_PATH, url.encodedPath)
        assertEquals(thumbPath, url.queryParameter(PlexImageUrl.ORIGINAL_PATH_PARAM))
        assertEquals(token, url.queryParameter(PlexImageUrl.TOKEN_PARAM))
        assertEquals("432", url.queryParameter("width"))
        assertEquals("648", url.queryParameter("height"))
    }

    @Test
    fun `falls back to a loadable url when the server refuses the resize`() {
        val url = PlexImageUrl.build(serverUrl, token, thumbPath, 432, 648).toHttpUrl()

        val fullSizeUrl = url.newBuilder()
            .encodedPath(requireNotNull(url.queryParameter(PlexImageUrl.ORIGINAL_PATH_PARAM)))
            .query(null)
            .addQueryParameter(PlexImageUrl.TOKEN_PARAM, token)
            .build()

        assertEquals(thumbPath, fullSizeUrl.encodedPath)
        assertEquals(token, fullSizeUrl.queryParameter(PlexImageUrl.TOKEN_PARAM))
    }
}
