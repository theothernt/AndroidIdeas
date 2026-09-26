package com.neilturner.playerexp.data.plex

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Keeps posters loading on a server that will not resize.
 *
 * [PlexImageUrl] asks Plex's photo transcode endpoint for a poster at card size. A server that
 * refuses that request would otherwise leave the card blank, so the request is retried once
 * against the original thumb path. Both responses are logged with their byte counts, which is the
 * quickest way to confirm from logcat whether the server really did hand back a smaller image.
 */
class PlexImageFallbackInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val originalPath = request.url.queryParameter(PlexImageUrl.ORIGINAL_PATH_PARAM)
        if (request.url.encodedPath != PlexImageUrl.TRANSCODE_PATH || originalPath == null) {
            return chain.proceed(request)
        }

        val requestedSize = "${request.url.queryParameter("width")}x" +
            request.url.queryParameter("height")

        val resized = chain.proceed(request)
        val contentType = resized.header("Content-Type")
        if (resized.isSuccessful && contentType?.startsWith("image/") == true) {
            Log.d(LOG_TAG, "Resized $originalPath to $requestedSize: ${describe(resized)}")
            return resized
        }

        // Plex sometimes answers a refused transcode with 200 and an error page, so check the type.
        val refused = "HTTP ${resized.code} ${resized.message}, content-type ${contentType ?: "none"}"
        val token = request.url.queryParameter(PlexImageUrl.TOKEN_PARAM)
        if (token == null) {
            Log.w(LOG_TAG, "Server would not resize $originalPath ($refused) and there is no token to retry with")
            return resized
        }

        val fullSizeUrl = request.url.newBuilder()
            .encodedPath(originalPath)
            .query(null)
            .addQueryParameter(PlexImageUrl.TOKEN_PARAM, token)
            .build()
        resized.close()
        Log.w(LOG_TAG, "Server would not resize $originalPath ($refused); requesting full size")

        val fullSize = chain.proceed(request.newBuilder().url(fullSizeUrl).build())
        Log.d(LOG_TAG, "Full size $originalPath: ${describe(fullSize)}")
        return fullSize
    }

    private fun describe(response: Response): String =
        "${response.header("Content-Type")}, ${response.header("Content-Length") ?: "unknown"} bytes"

    private companion object {
        const val LOG_TAG = "PlexImage"
    }
}

/** The HTTP client Coil loads posters with. Separate from the one the Plex API and ExoPlayer use. */
fun plexImageHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(PlexImageFallbackInterceptor())
    .build()
