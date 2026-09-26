package com.neilturner.channelui.ui.screens

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.neilturner.channelui.ui.components.ChannelItem
import com.neilturner.channelui.ui.components.VideoPlayer
import com.neilturner.channelui.ui.components.VerticalRowPager
import com.neilturner.channelui.ui.viewmodel.ChannelViewModel
import org.koin.androidx.compose.koinViewModel

private const val COLUMNS = 5
private const val PAGE_SETTLE_DURATION_MS = 300
private val PAGER_HEIGHT = 140.dp

private val PageSettleSpec: AnimationSpec<Float> = tween(
    durationMillis = PAGE_SETTLE_DURATION_MS,
    easing = FastOutSlowInEasing
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ChannelViewModel = koinViewModel(),
    playWhenReady: Boolean = true
) {
    val channels by viewModel.channels.collectAsState()
    val currentStreamUrl by viewModel.currentStreamUrl.collectAsState()

    if (channels.isEmpty()) return

    Box(modifier = Modifier.fillMaxSize()) {
        // Video Background
        // Handle potential null URL or empty string
        currentStreamUrl?.let { url ->
            VideoPlayer(
                url = url,
                modifier = Modifier.fillMaxSize(),
                playWhenReady = playWhenReady
            )
        }

        // Gradient Overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.9f)
                        ),
                        startY = 300f
                    )
                )
        )

        VerticalRowPager(
            itemCount = channels.size,
            itemsPerRow = COLUMNS,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(PAGER_HEIGHT)
                .padding(horizontal = 32.dp, vertical = 16.dp),
            pageAnimationSpec = PageSettleSpec
        ) { index, itemModifier ->
            val channel = channels[index]
            ChannelItem(
                channel = channel,
                onClick = { viewModel.playChannel(channel) },
                modifier = itemModifier
            )
        }
    }
}
