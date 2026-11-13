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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.neilturner.visualmashup.data.SourceImage

private const val TRANSITION_MS = 1_200
private const val FADE_MS = TRANSITION_MS / 2
private const val MAX_BLUR = 12f

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
private fun BlurCrossfade(
    targetState: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    val blur = remember { Animatable(0f) }

    LaunchedEffect(targetState) {
        blur.snapTo(0f)
        blur.animateTo(1f, tween(TRANSITION_MS / 2))
        blur.animateTo(0f, tween(TRANSITION_MS / 2))
    }

    Crossfade(
        targetState = targetState,
        animationSpec = tween(TRANSITION_MS),
        modifier = modifier.graphicsLayer {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val radius = (blur.value * MAX_BLUR).dp.toPx()
                renderEffect = BlurEffect(radius, radius)
            }
        },
        content = content,
    )
}