package com.neilturner.overlayparty.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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

@Composable
fun TextBlock(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    showBackground: Boolean = true
) {
    Box(
        modifier = modifier
            .animateContentSize()
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(
                text = text,
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
    }
}
