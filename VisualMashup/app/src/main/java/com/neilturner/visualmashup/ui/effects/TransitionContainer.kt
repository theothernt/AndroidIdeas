package com.neilturner.visualmashup.ui.effects

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.neilturner.visualmashup.data.SourceImage

private const val TRANSITION_MS = 1_200
private const val FADE_MS = TRANSITION_MS / 2

/**
 * Renders the current [targetState] and animates to it using [transition].
 *
 * The content lambda is given the target index; the container only owns the animation between
 * states, so the same content (for example [com.neilturner.visualmashup.ui.effects.EffectImage])
 * works for every transition.
 */
@Composable
fun TransitionContainer(
    transition: Transition,
    targetState: Int,
    images: List<SourceImage>,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    when (transition) {
        Transition.NONE -> key(targetState) {
            Box(modifier) { content(targetState) }
        }

        Transition.CROSSFADE -> Crossfade(
            targetState = targetState,
            animationSpec = tween(TRANSITION_MS),
            modifier = modifier,
            content = content,
        )

        Transition.DIP_TO_BLACK -> AnimatedContent(
            targetState = targetState,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(tween(FADE_MS, delayMillis = FADE_MS)),
                    initialContentExit = fadeOut(tween(FADE_MS)),
                )
            },
            modifier = modifier,
            content = { index -> content(index) },
        )

        Transition.SLIDE -> AnimatedContent(
            targetState = targetState,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = slideInHorizontally(tween(TRANSITION_MS)) { it } +
                        fadeIn(tween(FADE_MS)),
                    initialContentExit = slideOutHorizontally(tween(TRANSITION_MS)) { -it } +
                        fadeOut(tween(FADE_MS)),
                )
            },
            modifier = modifier,
            content = { index -> content(index) },
        )

        Transition.BLUR_CROSSFADE -> BlurCrossfade(
            targetState = targetState,
            modifier = modifier,
            content = content,
        )

        Transition.ZOOM -> AnimatedContent(
            targetState = targetState,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = scaleIn(tween(TRANSITION_MS), initialScale = 0.8f) +
                        fadeIn(tween(FADE_MS)),
                    initialContentExit = scaleOut(tween(TRANSITION_MS), targetScale = 1.2f) +
                        fadeOut(tween(FADE_MS)),
                )
            },
            modifier = modifier,
            content = { index -> content(index) },
        )

        Transition.FEATHERED_WIPE ->
            FeatheredWipeTransition(
                targetState = targetState,
                modifier = modifier,
                content = content,
            )

        Transition.WIPE,
        Transition.IRIS,
        Transition.GRADIENT_WIPE,
        Transition.PARALLAX,
        Transition.FLIP,
        Transition.FOLD,
        Transition.PIXELATE,
        -> RandomEffectContainer(
            effect = transition.toRandomEffect(),
            targetState = targetState,
            images = images,
            widthPx = widthPx,
            heightPx = heightPx,
            modifier = modifier,
            content = content,
        )
    }
}

private fun Transition.toRandomEffect(): RandomEffect = when (this) {
    Transition.WIPE -> RandomEffect.WIPE
    Transition.IRIS -> RandomEffect.IRIS
    Transition.GRADIENT_WIPE -> RandomEffect.GRADIENT_WIPE
    Transition.PARALLAX -> RandomEffect.PARALLAX
    Transition.FLIP -> RandomEffect.FLIP
    Transition.FOLD -> RandomEffect.FOLD
    Transition.PIXELATE -> RandomEffect.PIXELATE
    else -> error("$this is not a random effect")
}

@Composable
private fun FeatheredWipeTransition(
    targetState: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    var previousIndex by remember { mutableIntStateOf(targetState) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(targetState) {
        if (targetState != previousIndex) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(TRANSITION_MS))
            previousIndex = targetState
        }
    }

    Box(modifier) {
        // Outgoing image stays fully drawn underneath.
        content(previousIndex)
        // Incoming image is revealed with a soft-edged alpha mask.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .featheredHorizontalWipe(progress = { progress.value }),
        ) {
            content(targetState)
        }
    }
}

/**
 * Soft-edged horizontal wipe. The incoming composable is isolated into an offscreen buffer while
 * wiping, then masked with a moving gradient using [BlendMode.DstIn].
 */
private fun Modifier.featheredHorizontalWipe(
    progress: () -> Float,
    featherFraction: Float = 0.15f,
): Modifier = this
    .graphicsLayer {
        val p = progress()
        compositingStrategy = if (p > 0f && p < 1f) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithContent {
        val p = progress().coerceIn(0f, 1f)
        if (p <= 0f) return@drawWithContent

        drawContent()

        if (p < 1f) {
            val leadingEdge = p * (1f + featherFraction)
            val startX = (leadingEdge - featherFraction) * size.width
            val endX = leadingEdge * size.width
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    start = Offset(startX, 0f),
                    end = Offset(endX, 0f),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

@Composable
private fun BlurCrossfade(
    targetState: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    Box(modifier) {
        // Low-res blurred backdrop (1/16 the pixels of a full-screen blur).
        Crossfade(
            targetState = targetState,
            animationSpec = tween(TRANSITION_MS),
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val scale = maxWidth / 64.dp
                Box(
                    modifier = Modifier
                        .size(64.dp, 36.dp)
                        .align(Alignment.Center)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                renderEffect = BlurEffect(8f, 8f)
                            }
                        },
                ) {
                    content(index)
                }
            }
        }
        // Sharp crossfade on top.
        Crossfade(
            targetState = targetState,
            animationSpec = tween(TRANSITION_MS),
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
    }
}