package com.neilturner.visualmashup.ui.effects

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.neilturner.visualmashup.data.SourceImage
import kotlin.math.roundToInt

private const val TRANSITION_MS = 1_200
private const val FADE_MS = TRANSITION_MS / 2

/**
 * Renders [images] one at a time, using [effect] for the change between them. The content lambda is
 * given the target index, so the Ken Burns [EffectImage] (or any other per-image content) is what gets
 * transitioned. Pixelate is the exception: it needs the image URLs to pre-render pixelation levels.
 */
@Composable
fun RandomEffectContainer(
    effect: RandomEffect,
    targetState: Int,
    images: List<SourceImage>,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    when (effect) {
        RandomEffect.WIPE ->
            RevealTransition(targetState, RevealKind.WIPE, modifier, content)

        RandomEffect.IRIS ->
            RevealTransition(targetState, RevealKind.IRIS, modifier, content)

        RandomEffect.GRADIENT_WIPE ->
            RevealTransition(targetState, RevealKind.GRADIENT, modifier, content)

        RandomEffect.PARALLAX ->
            ParallaxTransition(targetState, modifier, content)

        RandomEffect.FLIP ->
            FlipTransition(targetState, modifier, shadow = false, content)

        RandomEffect.FOLD ->
            FlipTransition(targetState, modifier, shadow = true, content)

        RandomEffect.PIXELATE ->
            PixelateTransition(targetState, images, widthPx, heightPx, modifier)
    }
}

private enum class RevealKind { WIPE, IRIS, GRADIENT }

@Composable
private fun RevealTransition(
    targetState: Int,
    reveal: RevealKind,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    var previousIndex by remember { mutableIntStateOf(targetState) }
    val progress = remember { Animatable(1f) }
    val irisPath = remember { Path() }

    LaunchedEffect(targetState) {
        if (targetState != previousIndex) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(TRANSITION_MS))
            previousIndex = targetState
        }
    }

    Box(modifier) {
        content(previousIndex)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val p = progress.value
                    when (reveal) {
                        RevealKind.WIPE -> clipRect(right = size.width * p) {
                            this@drawWithContent.drawContent()
                        }
                        RevealKind.IRIS -> {
                            val radius = p * maxOf(size.width, size.height)
                            irisPath.reset()
                            irisPath.addOval(
                                Rect(
                                    size.width / 2f - radius,
                                    size.height / 2f - radius,
                                    size.width / 2f + radius,
                                    size.height / 2f + radius,
                                ),
                            )
                            clipPath(irisPath) {
                                this@drawWithContent.drawContent()
                            }
                        }
                        RevealKind.GRADIENT -> {
                            this@drawWithContent.drawContent()
                            // Opaque up to the wipe front, then a short soft edge. The middle
                            // color stop keeps the revealed side fully opaque at the end.
                            val feather = size.width * 0.08f
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color.Black, Color.Black, Color.Transparent),
                                    start = Offset(size.width * p - feather, 0f),
                                    end = Offset(size.width * p + feather, 0f),
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    }
                },
        ) {
            content(targetState)
        }
    }
}

@Composable
private fun ParallaxTransition(
    targetState: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = slideInHorizontally(tween(TRANSITION_MS)) { it } +
                    fadeIn(tween(FADE_MS)),
                initialContentExit = slideOutHorizontally(tween(TRANSITION_MS)) { -it / 2 } +
                    fadeOut(tween(FADE_MS)),
            )
        },
        modifier = modifier,
    ) { index ->
        content(index)
    }
}

@Composable
private fun FlipTransition(
    targetState: Int,
    modifier: Modifier = Modifier,
    shadow: Boolean,
    content: @Composable (Int) -> Unit,
) {
    var previousIndex by remember { mutableIntStateOf(targetState) }
    val progress = remember { Animatable(1f) }
    val density = LocalDensity.current

    LaunchedEffect(targetState) {
        if (targetState != previousIndex) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(TRANSITION_MS))
            previousIndex = targetState
        }
    }

    Box(modifier) {
        RotatingContent(
            index = previousIndex,
            rotationY = -90f * progress.value,
            density = density,
            shadow = shadow,
            content = content,
        )
        RotatingContent(
            index = targetState,
            rotationY = 90f * (1f - progress.value),
            density = density,
            shadow = shadow,
            content = content,
        )
    }
}

@Composable
private fun RotatingContent(
    index: Int,
    rotationY: Float,
    density: Density,
    shadow: Boolean,
    content: @Composable (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.rotationY = rotationY
                cameraDistance = 12f * density.density
            },
    ) {
        content(index)
        if (shadow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                        ),
                    ),
            )
        }
    }
}

private const val PIXEL_WIDTH = 48
private const val PIXEL_HEIGHT = 27

@Composable
private fun PixelateTransition(
    targetState: Int,
    images: List<SourceImage>,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var previousIndex by remember { mutableIntStateOf(targetState) }
    val progress = remember { Animatable(1f) }
    var oldLevels by remember { mutableStateOf<List<PixelationLevel>>(emptyList()) }
    var newLevels by remember { mutableStateOf<List<PixelationLevel>>(emptyList()) }

    LaunchedEffect(targetState) {
        val new = PixelationCache.getLevels(context, images[targetState], widthPx, heightPx)
        newLevels = new

        if (targetState != previousIndex) {
            val old = PixelationCache.getLevels(context, images[previousIndex], widthPx, heightPx)
            oldLevels = old

            if (old.isNotEmpty() && new.isNotEmpty()) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(TRANSITION_MS))
                previousIndex = targetState
            }
        }
    }

    Canvas(modifier = modifier) {
        val old = oldLevels
        val new = newLevels
        val fullWidth = size.width.toInt()
        val fullHeight = size.height.toInt()

        if (old.isNotEmpty() && new.isNotEmpty()) {
            val p = progress.value
            val oldIndex = (p * (old.size - 1)).roundToInt()
            val newIndex = ((1f - p) * (new.size - 1)).roundToInt()
            drawLevel(old[oldIndex], alpha = 1f - p, fullWidth, fullHeight)
            drawLevel(new[newIndex], alpha = p, fullWidth, fullHeight)
        } else if (new.isNotEmpty()) {
            drawLevel(new.last(), alpha = 1f, fullWidth, fullHeight)
        }
    }
}

private fun DrawScope.drawLevel(
    level: PixelationLevel,
    alpha: Float,
    fullWidth: Int,
    fullHeight: Int,
) {
    drawImage(
        image = level.image,
        srcOffset = IntOffset(level.srcLeft, level.srcTop),
        srcSize = IntSize(level.srcWidth, level.srcHeight),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(fullWidth, fullHeight),
        alpha = alpha,
        filterQuality = FilterQuality.None,
    )
}