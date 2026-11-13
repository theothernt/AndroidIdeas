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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
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
import com.neilturner.visualmashup.data.maxImageSize
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

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
            FlipTransition(targetState, modifier, content)

        RandomEffect.FOLD ->
            PageFoldTransition(targetState, modifier, content)

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
    val progress = remember(targetState) { Animatable(0f) }
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
    content: @Composable (Int) -> Unit,
) {
    var previousIndex by remember { mutableIntStateOf(targetState) }
    val progress = remember(targetState) { Animatable(0f) }
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
            content = content,
        )
        RotatingContent(
            index = targetState,
            rotationY = 90f * (1f - progress.value),
            density = density,
            content = content,
        )
    }
}

@Composable
private fun RotatingContent(
    index: Int,
    rotationY: Float,
    density: Density,
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
    }
}

/**
 * A realistic page turn: the screen is split along a vertical centre spine into a static left half
 * of the current page, a static right half of the next page, and an animated flap.
 *
 * The flap shows the right half of the current page rotating 0° → -90° while [progress] goes
 * 0 → 0.5, then swaps to the left half of the next page rotating +90° → 0° while it goes 0.5 → 1.
 * Shading on the flap and a cast shadow along the spine give it some physical depth.
 */
@Composable
private fun PageFoldTransition(
    targetState: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    var previousIndex by remember { mutableIntStateOf(targetState) }
    // Reset during composition (not in an effect) so the frame that first shows a new target is
    // already at progress 0; otherwise the new image flashes fully drawn for one frame.
    val progress = remember(targetState) { Animatable(0f) }
    val density = LocalDensity.current

    LaunchedEffect(targetState) {
        if (targetState != previousIndex) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(TRANSITION_MS))
            previousIndex = targetState
        }
    }

    Box(modifier) {
        // 1. Static left half of the current page.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val p = progress.value
                    if (p < 1f) {
                        clipRect(right = size.width / 2f) {
                            this@drawWithContent.drawContent()
                            // Shadow cast onto the left page as the flap closes over it.
                            if (p > 0.5f) {
                                val shadowAlpha = ((p - 0.5f) * 2f) * 0.45f
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = shadowAlpha),
                                        ),
                                        startX = size.width * 0.25f,
                                        endX = size.width / 2f,
                                    ),
                                    size = Size(size.width / 2f, size.height),
                                )
                            }
                        }
                    }
                },
        ) {
            content(previousIndex)
        }

        // 2. Static right half of the next page.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val p = progress.value
                    if (p > 0f) {
                        clipRect(left = size.width / 2f) {
                            this@drawWithContent.drawContent()
                            // Shadow cast onto the newly exposed right page while the flap is lifted.
                            if (p < 0.5f) {
                                val shadowAlpha = (1f - (p * 2f)) * 0.45f
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = shadowAlpha),
                                            Color.Transparent,
                                        ),
                                        startX = size.width / 2f,
                                        endX = size.width * 0.75f,
                                    ),
                                    topLeft = Offset(size.width / 2f, 0f),
                                    size = Size(size.width / 2f, size.height),
                                )
                            }
                        }
                    }
                },
        ) {
            content(targetState)
        }

        // 3A. Turning flap, first half: right half of the current page lifting up.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    alpha = if (p <= 0.5f) 1f else 0f
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    rotationY = -180f * p
                    cameraDistance = 24f * density.density
                }
                .drawWithContent {
                    val p = progress.value.coerceIn(0f, 1f)
                    if (p <= 0.5f) {
                        clipRect(left = size.width / 2f) {
                            this@drawWithContent.drawContent()
                            // Darken the flap as it angles toward 90 degrees.
                            val foldShade = sin(p * PI).toFloat() * 0.30f
                            drawRect(
                                color = Color.Black.copy(alpha = foldShade),
                                topLeft = Offset(size.width / 2f, 0f),
                                size = Size(size.width / 2f, size.height),
                            )
                        }
                    }
                },
        ) {
            content(previousIndex)
        }

        // 3B. Turning flap, second half: left half of the next page settling down.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    alpha = if (p > 0.5f) 1f else 0f
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    rotationY = 180f * (1f - p)
                    cameraDistance = 24f * density.density
                }
                .drawWithContent {
                    val p = progress.value.coerceIn(0f, 1f)
                    if (p > 0.5f) {
                        clipRect(right = size.width / 2f) {
                            this@drawWithContent.drawContent()
                            val foldShade = sin(p * PI).toFloat() * 0.30f
                            drawRect(
                                color = Color.Black.copy(alpha = foldShade),
                                size = Size(size.width / 2f, size.height),
                            )
                        }
                    }
                },
        ) {
            content(targetState)
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
            drawLevel(
                level = old[oldIndex],
                alpha = 1f - p,
                fullWidth = fullWidth,
                fullHeight = fullHeight,
                filterQuality = filterFor(oldIndex),
            )
            drawLevel(
                level = new[newIndex],
                alpha = p,
                fullWidth = fullWidth,
                fullHeight = fullHeight,
                filterQuality = filterFor(newIndex),
            )
        } else if (new.isNotEmpty()) {
            // No transition yet: show the full-resolution level.
            drawLevel(
                level = new.first(),
                alpha = 1f,
                fullWidth = fullWidth,
                fullHeight = fullHeight,
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

/**
 * Level 0 is the full-resolution image, so it is filtered like any other image (bilinear). The
 * lower levels are deliberately blocky, so they keep nearest-neighbour sampling.
 */
private fun filterFor(levelIndex: Int): FilterQuality =
    if (levelIndex == 0) FilterQuality.Low else FilterQuality.None

private fun DrawScope.drawLevel(
    level: PixelationLevel,
    alpha: Float,
    fullWidth: Int,
    fullHeight: Int,
    filterQuality: FilterQuality,
) {
    drawImage(
        image = level.image,
        srcOffset = IntOffset(level.srcLeft, level.srcTop),
        srcSize = IntSize(level.srcWidth, level.srcHeight),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(fullWidth, fullHeight),
        alpha = alpha,
        filterQuality = filterQuality,
    )
}