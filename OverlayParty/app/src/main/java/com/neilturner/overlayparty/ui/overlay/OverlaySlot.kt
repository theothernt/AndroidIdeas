package com.neilturner.overlayparty.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neilturner.overlayparty.ui.components.MultiItemBlock
import com.neilturner.overlayparty.ui.components.TextBlock

/**
 * A composable slot that renders any [OverlayContent] type.
 * This provides a unified way to display different overlay content types.
 *
 * @param content The overlay content to render, or null for an empty slot
 * @param modifier Modifier to apply to the overlay
 */
@Composable
fun OverlaySlot(
    content: OverlayContent?,
    modifier: Modifier = Modifier,
    showBackground: Boolean = true
) {
    if (content == null) return

    val animationType = content.animationType

    if (animationType == OverlayAnimationType.FADE_AND_REPLACE) {
        AnimatedContent(
            targetState = content,
            transitionSpec = {
                (fadeIn(animationSpec = tween(700, delayMillis = 700)) togetherWith 
                 fadeOut(animationSpec = tween(700)))
                 .using(
                     SizeTransform { _, _ ->
                         // Delay the size animation to match the fade-in start (after fade-out completes)
                         tween(durationMillis = 700, delayMillis = 700)
                     }
                 )
            },
            label = "OverlayFade"
        ) { targetContent ->
            RenderOverlayContent(
                content = targetContent,
                modifier = modifier,
                showBackground = showBackground,
                animateSize = false // Size animation handled by AnimatedContent implicitly or not needed during fade
            )
        }
    } else {
        // CONTENT_RESIZING
        RenderOverlayContent(
            content = content,
            modifier = modifier,
            showBackground = showBackground,
            animateSize = true
        )
    }
}

@Composable
private fun RenderOverlayContent(
    content: OverlayContent,
    modifier: Modifier,
    showBackground: Boolean,
    animateSize: Boolean
) {
    when (content) {
        is OverlayContent.TextOnly -> TextBlock(
            text = content.text,
            modifier = modifier,
            showBackground = showBackground,
            animateSize = animateSize,
            scale = content.scale,
            padding = content.padding
        )
        is OverlayContent.IconWithText -> TextBlock(
            text = content.text,
            icon = content.icon,
            iconPosition = content.iconPosition,
            modifier = modifier,
            showBackground = showBackground,
            animateSize = animateSize,
            scale = content.scale,
            padding = content.padding
        )
        is OverlayContent.MultiItemContent -> MultiItemBlock(
            items = content.items,
            modifier = modifier,
            showBackground = showBackground,
            animateSize = animateSize,
            padding = content.padding
        )
        is OverlayContent.VerticalStack -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End // Default to end alignment for stacks, usually top-right or bottom-right
            ) {
                content.items.forEach { childContent ->
                    OverlaySlot(
                        content = childContent,
                        modifier = Modifier, // Modifier is applied to the stack container, not individual items
                        showBackground = showBackground
                    )
                }
            }
        }
    }
}
