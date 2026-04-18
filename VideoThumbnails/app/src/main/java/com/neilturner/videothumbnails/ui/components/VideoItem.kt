package com.neilturner.videothumbnails.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.neilturner.videothumbnails.data.Video
import com.neilturner.videothumbnails.ui.theme.VideoThumbnailsTheme
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

/**
 * Represents the current state of thumbnail generation.
 */
enum class ThumbnailState {
    LOADING,
    SUCCESS,
    ERROR,
}

// Semaphore to limit concurrent thumbnail generation to 2 at a time
private val thumbnailSemaphore = Semaphore(permits = 2)

suspend fun clearThumbnailCache(context: Context) {
    withContext(Dispatchers.IO) {
        val thumbnailDir = File(context.filesDir, "thumbnails")
        thumbnailDir.listFiles { _, name -> name.startsWith("thumb_") }?.forEach {
            it.delete()
        }
    }
}

/**
 * Generates a thumbnail for a video URL.
 * @return The cached file path if successful, null if failed.
 */
suspend fun generateVideoThumbnail(
    context: Context,
    videoUrl: String,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): String? {
    return withContext(dispatcher) {
        try {
            // Acquire semaphore permit to limit concurrent generation
            thumbnailSemaphore.withPermit {
                // Create thumbnails directory if it doesn't exist
                val thumbnailDir = File(context.filesDir, "thumbnails")
                if (!thumbnailDir.exists()) {
                    thumbnailDir.mkdirs()
                }

                // Check if thumbnail already exists
                val thumbnailFile = File(thumbnailDir, "thumb_${videoUrl.hashCode()}.jpg")
                if (thumbnailFile.exists()) {
                    return@withPermit thumbnailFile.absolutePath
                }

                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoUrl, HashMap<String, String>())

                val bitmap = retriever.getFrameAtTime(
                    1_000_000, // 1 second
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                retriever.release()

                bitmap?.let {
                    FileOutputStream(thumbnailFile).use { out ->
                        it.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    it.recycle()
                    thumbnailFile.absolutePath
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

suspend fun generateVideoThumbnailWithFallback(
    context: Context,
    video: Video,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): String? {
    return withContext(dispatcher) {
        try {
            // Acquire semaphore permit to limit concurrent generation
            thumbnailSemaphore.withPermit {
                // Create thumbnails directory if it doesn't exist
                val thumbnailDir = File(context.filesDir, "thumbnails")
                if (!thumbnailDir.exists()) {
                    thumbnailDir.mkdirs()
                }

                // Try each video URL in preference order
                val videoUrls = video.getVideoUrlsInPreferenceOrder()
                
                for (url in videoUrls) {
                    try {
                        // Check if thumbnail already exists for this URL
                        val thumbnailFile = File(thumbnailDir, "thumb_${url.hashCode()}.jpg")
                        if (thumbnailFile.exists()) {
                            return@withPermit thumbnailFile.absolutePath
                        }

                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(url, HashMap<String, String>())

                        val bitmap = retriever.getFrameAtTime(
                            1_000_000, // 1 second
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        retriever.release()

                        bitmap?.let {
                            FileOutputStream(thumbnailFile).use { out ->
                                it.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                            it.recycle()
                            return@withPermit thumbnailFile.absolutePath
                        }
                    } catch (e: Exception) {
                        // Log error and try next URL
                        println("Failed to generate thumbnail for URL $url: ${e.message}")
                        continue
                    }
                }
                
                // All URLs failed
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

/**
 * Small spinning loading indicator for thumbnail generation.
 */
@Composable
private fun LoadingIndicator(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Box(
        modifier = modifier
            .size(16.dp)
            .rotate(rotation),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(14.dp)
        ) {
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/**
 * Small red X indicator for failed thumbnail generation.
 */
@Composable
private fun ErrorIndicator(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(Color.Red.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        // Draw X shape
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(10.dp)
        ) {
            val strokeWidth = 2.dp.toPx()
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                end = androidx.compose.ui.geometry.Offset(0f, size.height),
                strokeWidth = strokeWidth,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoItem(
    video: Video,
    onClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
    thumbnailDispatcher: CoroutineDispatcher = koinInject(named("ThumbnailDispatcher"))
) {
    val context = LocalContext.current
    var thumbnailPath by remember(video.id) { mutableStateOf<String?>(null) }
    var thumbnailState by remember(video.id) { mutableStateOf(ThumbnailState.LOADING) }

    LaunchedEffect(video.id) {
        if (thumbnailState != ThumbnailState.SUCCESS) {
            thumbnailState = ThumbnailState.LOADING
            try {
                val result = generateVideoThumbnailWithFallback(context, video, thumbnailDispatcher)
                
                if (result != null) {
                    thumbnailPath = result
                    thumbnailState = ThumbnailState.SUCCESS
                } else {
                    thumbnailState = ThumbnailState.ERROR
                }
            } catch (e: Exception) {
                thumbnailState = ThumbnailState.ERROR
            }
        }
    }

    Card(
        onClick = { onClick(video) },
        modifier = modifier.aspectRatio(16f / 9f),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Show thumbnail if available
            if (thumbnailState == ThumbnailState.SUCCESS) {
                thumbnailPath?.let { path ->
                    AsyncImage(
                        model = path,
                        contentDescription = video.getDisplayTitle(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }

            // Gradient scrim for better text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 100f
                        )
                    )
            )

            // Status indicator in top-right corner
            when (thumbnailState) {
                ThumbnailState.LOADING -> {
                    LoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    )
                }
                ThumbnailState.ERROR -> {
                    ErrorIndicator(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    )
                }
                ThumbnailState.SUCCESS -> {
                    // No indicator needed
                }
            }

            Text(
                text = video.getDisplayTitle(),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

@Preview
@Composable
private fun VideoItemPreview() {
    VideoThumbnailsTheme {
        VideoItem(
            video = Video(
                id = "preview_video",
                title = "Sample Video",
                accessibilityLabel = "Sample Video Location",
                timeOfDay = "day",
                scene = "nature",
                url1080H264 = "https://example.com/video.mp4"
            ),
            onClick = {}
        )
    }
}
