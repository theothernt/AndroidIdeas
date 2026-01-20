package com.neilturner.overlayparty.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.overlayparty.ui.overlay.IconPosition
import com.neilturner.overlayparty.ui.overlay.OverlayItem

@Composable
fun TextBlock(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPosition: IconPosition = IconPosition.LEADING,
    showBackground: Boolean = true,
    animateSize: Boolean = true
) {
    OverlayContainer(modifier, showBackground, animateSize) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null && iconPosition == IconPosition.LEADING) {
                OverlayIcon(icon)
            }

            OverlayText(text, showBackground)

            if (icon != null && iconPosition == IconPosition.TRAILING) {
                OverlayIcon(icon)
            }
        }
    }
}

@Composable
fun MultiItemBlock(
    items: List<OverlayItem>,
    modifier: Modifier = Modifier,
    showBackground: Boolean = true,
    animateSize: Boolean = true
) {
    OverlayContainer(modifier, showBackground, animateSize) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                when (item) {
                    is OverlayItem.Text -> OverlayText(item.text, showBackground)
                    is OverlayItem.Icon -> OverlayIcon(item.icon)
                }
            }
        }
    }
}

@Composable
private fun OverlayContainer(
    modifier: Modifier,
    showBackground: Boolean,
    animateSize: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .then(if (animateSize) Modifier.animateContentSize() else Modifier)
            .then(
                if (showBackground) {
                    Modifier.background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .padding(8.dp)
    ) {
        content()
    }
}

@Composable
private fun OverlayText(
    text: String,
    showBackground: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = if (showBackground) {
            MaterialTheme.typography.bodyLarge
        } else {
            MaterialTheme.typography.bodyLarge.copy(
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(2f, 2f),
                    blurRadius = 2f
                )
            )
        },
        color = Color.White
    )
}

@Composable
private fun OverlayIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
    )
}
