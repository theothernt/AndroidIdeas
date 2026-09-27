package com.neilturner.videothumbnails.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neilturner.videothumbnails.data.Video
import com.neilturner.videothumbnails.ui.theme.VideoThumbnailsTheme

private const val HIDDEN_LABEL_ALPHA = 0.4f
private const val GRAYSCALE_FADE_MILLIS = 300

@OptIn(ExperimentalTvMaterial3Api::class)
@Stable
@Composable
fun VideoItem(
    video: Video,
    isHidden: Boolean,
    onClick: (Video) -> Unit,
    onLongClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val gradientScrim =
        remember {
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                startY = 100f,
            )
        }

    val grayscaleAmount by animateFloatAsState(
        targetValue = if (isHidden) 1f else 0f,
        animationSpec = tween(durationMillis = GRAYSCALE_FADE_MILLIS),
        label = "grayscaleAmount",
    )

    val grayscaleFilter =
        remember(grayscaleAmount) {
            ColorFilter.colorMatrix(
                ColorMatrix().apply { setToSaturation(1f - grayscaleAmount) },
            )
        }

    Card(
        onClick = { onClick(video) },
        onLongClick = { onLongClick(video) },
        modifier = modifier.aspectRatio(16f / 9f),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        glow =
            CardDefaults.glow(
                glow = Glow.None,
                focusedGlow = Glow.None,
                pressedGlow = Glow.None,
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val assetPath = video.thumbnailAssetPath
            if (assetPath != null) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data("file:///android_asset/$assetPath")
                            .crossfade(false)
                            .build(),
                    contentDescription = video.getDisplayTitle(),
                    contentScale = ContentScale.Crop,
                    colorFilter = grayscaleFilter.takeIf { grayscaleAmount > 0f },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(gradientScrim),
            )

            Text(
                text = video.getDisplayTitle(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = if (isHidden) HIDDEN_LABEL_ALPHA else 1f),
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
            )
        }
    }
}

@Preview
@Composable
private fun VideoItemPreview() {
    VideoThumbnailsTheme {
        VideoItem(
            video =
                Video(
                    id = "preview_video",
                    title = "Sample Video",
                    accessibilityLabel = "Sample Video Location",
                    timeOfDay = "day",
                    scene = "nature",
                    url1080H264 = "https://example.com/video.mp4",
                ),
            isHidden = false,
            onClick = {},
            onLongClick = {},
        )
    }
}
