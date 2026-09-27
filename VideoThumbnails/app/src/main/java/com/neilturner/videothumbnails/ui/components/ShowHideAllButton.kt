package com.neilturner.videothumbnails.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.videothumbnails.ui.theme.ActionButtonIdleLabel
import com.neilturner.videothumbnails.ui.theme.ActionButtonIdlePill
import com.neilturner.videothumbnails.ui.theme.RailFocusedLabel
import com.neilturner.videothumbnails.ui.theme.RailFocusedPill

private const val BUTTON_FADE_MILLIS = 200
private const val BUTTON_FOCUSED_SCALE = 1.02f
private val BUTTON_SHAPE = RoundedCornerShape(percent = 50)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ShowHideAllButton(
    label: String,
    onClick: () -> Unit,
    onNavigateDown: () -> Unit,
    onNavigateLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    val containerColor by animateColorAsState(
        targetValue = if (isFocused) RailFocusedPill else ActionButtonIdlePill,
        animationSpec = tween(durationMillis = BUTTON_FADE_MILLIS),
        label = "actionButtonPillColor",
    )

    val labelColor by animateColorAsState(
        targetValue = if (isFocused) RailFocusedLabel else ActionButtonIdleLabel,
        animationSpec = tween(durationMillis = BUTTON_FADE_MILLIS),
        label = "actionButtonLabelColor",
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused) BUTTON_FOCUSED_SCALE else 1f,
        animationSpec = tween(durationMillis = BUTTON_FADE_MILLIS),
        label = "actionButtonScale",
    )

    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = labelColor,
        maxLines = 1,
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.background(containerColor, BUTTON_SHAPE)
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    when {
                        event.type != KeyEventType.KeyDown -> {
                            false
                        }

                        event.key == Key.DirectionDown -> {
                            onNavigateDown()
                            true
                        }

                        event.key == Key.DirectionLeft -> {
                            onNavigateLeft()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 20.dp, vertical = 10.dp),
    )
}
