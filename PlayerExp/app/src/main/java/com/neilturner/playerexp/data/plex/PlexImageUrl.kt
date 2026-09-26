package com.neilturner.playerexp.data.plex

import io.ktor.http.URLBuilder

/**
 * Poster URLs.
 *
 * Plex serves artwork at whatever size it was scanned at, and 2:3 posters are commonly 1000x1500.
 * Adding `width` to a plain thumb path is ignored, so a card that draws a poster a few hundred
 * pixels across would still download and decode the full-size image. The photo transcode endpoint
 * is Plex's resize path: it takes the original thumb path plus the dimensions wanted and returns an
 * image already at that size.
 *
 *     /photo/:/transcode?url=<thumb path>&width=<w>&height=<h>&minSize=1&upscale=1
 *
 * Not every server accepts that request, so [PlexImageFallbackInterceptor] retries the plain thumb
 * path when it is refused. Sizes are in physical pixels, matching what Coil decodes to, so the
 * network and the decode agree on one number.
 */
object PlexImageUrl {
    /** Assigned rather than appended: the `:` must survive as `:/transcode`, not `%3A/transcode`. */
    const val TRANSCODE_PATH = "/photo/:/transcode"
    private val TRANSCODE_SEGMENTS = listOf("photo", ":", "transcode")
    const val ORIGINAL_PATH_PARAM = "url"
    const val TOKEN_PARAM = "X-Plex-Token"

    /**
     * TEMPORARY test switch. Off means a card loads the image Plex has stored, untouched, and Coil
     * does the downscaling itself, which is the way to see exactly what the server holds. It costs
     * the full-size download per poster, so it belongs on only while artwork is being checked.
     */
    const val USE_SERVER_SIDE_RESIZE = false

    fun build(
        serverUrl: String,
        accountToken: String,
        imagePath: String,
        widthPx: Int,
        heightPx: Int
    ): String = if (USE_SERVER_SIDE_RESIZE) {
        resized(serverUrl, accountToken, imagePath, widthPx, heightPx)
    } else {
        asStored(serverUrl, accountToken, imagePath)
    }

    fun resized(
        serverUrl: String,
        accountToken: String,
        imagePath: String,
        widthPx: Int,
        heightPx: Int
    ): String {
        val builder = URLBuilder(serverUrl.trimEnd('/'))
        builder.encodedPathSegments = TRANSCODE_SEGMENTS
        builder.parameters.append(ORIGINAL_PATH_PARAM, imagePath)
        builder.parameters.append("width", widthPx.toString())
        builder.parameters.append("height", heightPx.toString())
        builder.parameters.append("minSize", "1")
        builder.parameters.append("upscale", "1")
        builder.parameters.append(TOKEN_PARAM, accountToken)
        return builder.buildString()
    }

    /** The stored image itself, still token-authenticated, with no size asked of Plex. */
    fun asStored(serverUrl: String, accountToken: String, imagePath: String): String {
        val base = if (imagePath.startsWith("http")) imagePath else serverUrl.trimEnd('/') + imagePath
        val builder = URLBuilder(base)
        builder.parameters.append(TOKEN_PARAM, accountToken)
        return builder.buildString()
    }
}
