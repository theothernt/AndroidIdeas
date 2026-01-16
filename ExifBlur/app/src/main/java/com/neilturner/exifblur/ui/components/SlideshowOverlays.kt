package com.neilturner.exifblur.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.neilturner.exifblur.util.RamInfo

@Composable
fun ImageCountOverlay(
    isVisible: Boolean,
    currentImageIndex: Int,
    imageCount: Int,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        Box(
            modifier = modifier
                .padding(16.dp)
                .alpha(0.8F)
                .background(
                    color = Color.Black,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            val statusText = if (imageCount == 0) {
                "No images found"
            } else {
                "Image ${currentImageIndex + 1} of $imageCount"
            }
            Text(
                text = statusText,
                color = Color.White
            )
        }
    }
}

@Composable
fun MetadataOverlay(
    isVisible: Boolean,
    label: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && !label.isNullOrBlank(),
        enter = fadeIn(animationSpec = tween(durationMillis = 800)),
        exit = fadeOut(animationSpec = tween(durationMillis = 800)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .alpha(0.8F)
                .background(
                    color = Color.Black,
                    shape = RoundedCornerShape(8.dp)
                )
                .animateContentSize()
                .padding(12.dp)
        ) {
            Text(
                text = label ?: "",
                color = Color.White
            )
        }
    }
}

@Composable
fun RamUsageOverlay(
    ramInfo: RamInfo,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .alpha(0.8F)
            .background(
                color = Color.Black,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = "RAM: ${ramInfo.usedMemoryMB}MB/${ramInfo.maxMemoryMB}MB",
                color = Color.White
            )
            Text(
                text = "Usage: ${ramInfo.usagePercentage}%",
                color = if (ramInfo.usagePercentage > 80) Color.Red else Color.White
            )
            Text(
                text = "Native: ${ramInfo.nativeHeapMB}MB",
                color = Color.Gray
            )
        }
    }
}
