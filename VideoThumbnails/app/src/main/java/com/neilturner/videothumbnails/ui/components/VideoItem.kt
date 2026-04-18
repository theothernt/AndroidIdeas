package com.neilturner.videothumbnails.ui.components

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import coil3.request.ImageRequest
import com.neilturner.videothumbnails.data.Video
import com.neilturner.videothumbnails.ui.theme.VideoThumbnailsTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoItem(
    video: Video,
    onClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val drawableName = video.getThumbnailDrawableName()
    val drawableId = remember(drawableName) {
        context.resources.getIdentifier(
            drawableName,
            "drawable",
            context.packageName
        )
    }

    Card(
        onClick = { onClick(video) },
        modifier = modifier.aspectRatio(16f / 9f),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Show pre-generated thumbnail
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(drawableId)
                    .build(),
                contentDescription = video.getDisplayTitle(),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )

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
