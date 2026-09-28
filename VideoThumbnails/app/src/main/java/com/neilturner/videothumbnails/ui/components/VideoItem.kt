package com.neilturner.videothumbnails.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neilturner.videothumbnails.data.Video
import com.neilturner.videothumbnails.ui.theme.VideoThumbnailsTheme
import kotlinx.coroutines.delay

private const val HIDDEN_LABEL_ALPHA = 0.4f
private const val GRAYSCALE_FADE_MILLIS = 300
private const val THUMBNAIL_CROSSFADE_MILLIS = 120
private const val THUMBNAIL_REVEAL_STEP_MILLIS = 40
private const val THUMBNAIL_REVEAL_MAX_MILLIS = 320

private val THUMBNAIL_SHAPE = RoundedCornerShape(12.dp)
private val THUMBNAIL_BORDER =
    Border(
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        inset = 0.dp,
        shape = THUMBNAIL_SHAPE,
    )

@OptIn(ExperimentalTvMaterial3Api::class)
@Stable
@Composable
fun VideoItem(
    video: Video,
    isHidden: Boolean,
    onClick: (Video) -> Unit,
    onLongClick: (Video) -> Unit,
    revealDelayMillis: Int = 0,
    onReveal: () -> Unit = {},
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

    var isFocusedCard by remember { mutableStateOf(false) }
    var isRevealed by remember(video.id) { mutableStateOf(false) }

    val painter =
        rememberAsyncImagePainter(
            model =
                ImageRequest
                    .Builder(context)
                    .data("file:///android_asset/${video.thumbnailAssetPath}")
                    .crossfade(THUMBNAIL_CROSSFADE_MILLIS)
                    .build(),
        )
    val painterState by painter.state.collectAsState()

    LaunchedEffect(painterState, revealDelayMillis) {
        if (painterState is AsyncImagePainter.State.Success) {
            if (revealDelayMillis > 0 && !isFocusedCard) {
                delay(revealDelayMillis.toLong())
            }
            isRevealed = true
            onReveal()
        }
    }

    val revealAlpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = if (isFocusedCard) 0 else THUMBNAIL_CROSSFADE_MILLIS),
        label = "thumbnailRevealAlpha",
    )

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
        modifier =
            modifier
                .aspectRatio(16f / 9f)
                .onFocusChanged { focusState ->
                    isFocusedCard = focusState.isFocused
                    if (focusState.isFocused) {
                        isRevealed = true
                        onReveal()
                    }
                },
        shape = CardDefaults.shape(THUMBNAIL_SHAPE),
        glow =
            CardDefaults.glow(
                glow = Glow.None,
                focusedGlow = Glow.None,
                pressedGlow = Glow.None,
            ),
        border =
            CardDefaults.border(
                border = Border.None,
                focusedBorder = THUMBNAIL_BORDER,
                pressedBorder = Border.None,
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val assetPath = video.thumbnailAssetPath
            if (assetPath != null) {
                Image(
                    painter = painter,
                    contentDescription = video.getDisplayTitle(),
                    contentScale = ContentScale.Crop,
                    colorFilter = grayscaleFilter.takeIf { grayscaleAmount > 0f },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = revealAlpha },
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
