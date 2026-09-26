package com.neilturner.channelui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neilturner.channelui.data.Channel

private val TileShape = RoundedCornerShape(12.dp)
private val FOCUS_BORDER_WIDTH = 2.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelItem(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = ClickableSurfaceDefaults.shape(shape = TileShape),
        // The border is stroked centred on the shape, so inset it by half its width to keep the
        // white focus ring inside the tile instead of bleeding over the rounded corners
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(
                    width = FOCUS_BORDER_WIDTH,
                    color = Color.White
                ),
                inset = FOCUS_BORDER_WIDTH / 2,
                shape = TileShape
            )
        ),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1.05f,  // Reduced from 1.1f for better fit
            pressedScale = 0.98f,  // Subtle press effect
        )
        // No glow: it is drawn as a blurred shadow outside the shape, which shows as white
        // pixels around the corners against the video
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            channel.color,
                            channel.color.copy(alpha = 0.6f),
                        ),
                        tileMode = TileMode.Mirror
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}
