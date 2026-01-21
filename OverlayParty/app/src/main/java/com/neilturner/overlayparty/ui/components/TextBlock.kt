package com.neilturner.overlayparty.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.overlayparty.ui.overlay.IconPosition
import com.neilturner.overlayparty.ui.overlay.OverlayItem

private val OverlayShape = RoundedCornerShape(12.dp)
private val OverlayBackgroundColor = Color.Black.copy(alpha = 0.6f)

private val OverlayContentShadow = Shadow(
    color = Color.Black,
    offset = Offset(2f, 2f),
    blurRadius = 2f
)

@Composable
fun TextBlock(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPosition: IconPosition = IconPosition.LEADING,
    showBackground: Boolean = true,
    animateSize: Boolean = true,
    scale: Float = 1f,
    padding: Dp = 8.dp
) {
    OverlayContainer(modifier, showBackground, animateSize, padding) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null && iconPosition == IconPosition.LEADING) {
                OverlayIcon(icon, useShadow = !showBackground)
            }

            OverlayText(text, showBackground, scale = scale)

            if (icon != null && iconPosition == IconPosition.TRAILING) {
                OverlayIcon(icon, useShadow = !showBackground)
            }
        }
    }
}

@Composable
fun MultiItemBlock(
    items: List<OverlayItem>,
    modifier: Modifier = Modifier,
    showBackground: Boolean = true,
    animateSize: Boolean = true,
    padding: Dp = 8.dp
) {
    OverlayContainer(modifier, showBackground, animateSize, padding) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                when (item) {
                    is OverlayItem.Text -> OverlayText(item.text, showBackground, scale = item.scale)
                    is OverlayItem.Icon -> OverlayIcon(item.icon, useShadow = !showBackground)
                }
            }
        }
    }
}

private fun Modifier.overlayBackground(showBackground: Boolean): Modifier = this.then(
    if (showBackground) {
        Modifier.background(
            color = OverlayBackgroundColor,
            shape = OverlayShape
        )
    } else {
        Modifier
    }
)

@Composable
private fun OverlayContainer(
    modifier: Modifier,
    showBackground: Boolean,
    animateSize: Boolean,
    padding: Dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .then(if (animateSize) Modifier.animateContentSize() else Modifier)
            .overlayBackground(showBackground)
            .padding(padding)
    ) {
        content()
    }
}

private fun TextStyle.applyShadow(useShadow: Boolean): TextStyle = if (useShadow) {
    this.copy(shadow = OverlayContentShadow)
} else {
    this
}

@Composable
private fun OverlayText(
    text: String,
    showBackground: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    val useShadow = !showBackground
    val baseStyle = MaterialTheme.typography.bodyLarge
    val scaledStyle = baseStyle.copy(
        fontSize = baseStyle.fontSize * scale
    ).applyShadow(useShadow)

    Text(
        text = text,
        modifier = modifier,
        style = scaledStyle,
        color = Color.White
    )
}

@Composable
private fun OverlayIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    useShadow: Boolean = false
) {
    if (useShadow) {
        ShadowedIcon(icon, modifier)
    } else {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = modifier
        )
    }
}

@Composable
private fun ShadowedIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.offset(2.dp, 2.dp)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White
        )
    }
}