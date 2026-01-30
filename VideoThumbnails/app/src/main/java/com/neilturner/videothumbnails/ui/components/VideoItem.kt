package com.neilturner.videothumbnails.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

suspend fun clearThumbnailCache(context: Context) {
    withContext(Dispatchers.IO) {
        context.cacheDir.listFiles { _, name -> name.startsWith("thumb_") }?.forEach { 
            it.delete() 
        }
    }
}

suspend fun generateVideoThumbnail(
    context: Context,
    videoUrl: String,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): String? {
    return withContext(dispatcher) {
        try {
            // Check if thumbnail already exists in cache
            val thumbnailFile = File(context.cacheDir, "thumb_${videoUrl.hashCode()}.jpg")
            if (thumbnailFile.exists()) {
                return@withContext thumbnailFile.absolutePath
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
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
    var thumbnailPath by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(video.url1080H264, thumbnailPath) {
        if (thumbnailPath == null) {
            thumbnailPath = generateVideoThumbnail(context, video.url1080H264, thumbnailDispatcher)
        }
    }
    
    Card(
        onClick = { onClick(video) },
        modifier = modifier.aspectRatio(16f / 9f),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            thumbnailPath?.let { path ->
                AsyncImage(
                    model = path,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                )
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

            Text(
                text = video.title,
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
            video = Video(title = "Sample Video", url1080H264 = ""),
            onClick = {}
        )
    }
}
