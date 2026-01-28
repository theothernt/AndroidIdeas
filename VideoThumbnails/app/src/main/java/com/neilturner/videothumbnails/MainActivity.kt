package com.neilturner.videothumbnails

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.neilturner.videothumbnails.data.Video
import com.neilturner.videothumbnails.data.VideoResponse
import com.neilturner.videothumbnails.ui.theme.DarkGray
import com.neilturner.videothumbnails.ui.theme.VideoThumbnailsTheme
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showDarkGrey by remember { mutableStateOf(false) }
            val backgroundColor by animateColorAsState(
                targetValue = if (showDarkGrey) DarkGray else Color.Black,
                animationSpec = tween(durationMillis = 1000),
                label = "backgroundColorTransition"
            )

            val videoList = remember {
                val jsonString = resources.openRawResource(R.raw.videos).bufferedReader().use { it.readText() }
                json.decodeFromString<VideoResponse>(jsonString).assets
            }

            LaunchedEffect(Unit) {
                showDarkGrey = true
            }

            VideoThumbnailsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = SurfaceDefaults.colors(containerColor = backgroundColor)
                ) {
                    VideoGrid(videoList)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoGrid(videos: List<Video>, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(videos) { index, video ->
            VideoItem(
                video = video,
                modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoItem(video: Video, modifier: Modifier = Modifier) {
    Surface(
        onClick = { },
        modifier = modifier
            .aspectRatio(16f / 9f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VideoThumbnailsTheme {
        VideoGrid(listOf(Video("Test Video", "")))
    }
}
