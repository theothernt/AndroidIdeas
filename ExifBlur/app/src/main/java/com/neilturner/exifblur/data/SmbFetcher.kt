package com.neilturner.exifblur.data

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException

/** Coil keyer for SMB images: the URI string is stable across processes. */
class SmbImageKeyer : Keyer<SmbImage> {
    override fun key(
        data: SmbImage,
        options: Options,
    ): String? = data.uri.toString()
}

/**
 * Bridges Coil to the existing SambaImageProvider without changing that
 * provider. Bytes are staged to a temp file (kept off the heap) and deleted
 * when Coil closes the ImageSource.
 */
class SmbFetcher(
    private val data: SmbImage,
    private val options: Options,
    private val provider: SambaImageProvider,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val tempFile = File.createTempFile("coil_smb_", ".img", options.context.cacheDir)
        try {
            val input =
                provider.openInputStream(data.uri)
                    ?: throw IOException("Unable to open SMB image ${data.uri}")
            input.use { stream ->
                tempFile.outputStream().use { output -> stream.copyTo(output) }
            }
            return SourceFetchResult(
                source =
                    ImageSource(
                        file = tempFile.toOkioPath(),
                        fileSystem = options.fileSystem,
                        diskCacheKey = data.uri.toString(),
                        closeable = AutoCloseable { tempFile.delete() },
                    ),
                mimeType = null,
                dataSource = DataSource.NETWORK,
            )
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }
    }

    class Factory(
        private val provider: SambaImageProvider,
    ) : Fetcher.Factory<SmbImage> {
        override fun create(
            data: SmbImage,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = SmbFetcher(data, options, provider)
    }
}
