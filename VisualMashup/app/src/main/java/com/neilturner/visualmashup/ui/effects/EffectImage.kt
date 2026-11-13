package com.neilturner.visualmashup.ui.effects

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.neilturner.visualmashup.data.SourceImage
import kotlin.math.PI
import kotlin.math.cos

/**
 * Renders [image] with the selected [effect].
 *
 * [widthPx] and [heightPx] must match the size the image will occupy so the request is an exact
 * memory-cache hit with the preload in [com.neilturner.visualmashup.ui.screens.EffectViewModel].
 */
@Composable
fun EffectImage(
    effect: ImageEffect,
    image: SourceImage,
    durationMillis: Int,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    when (effect) {
        ImageEffect.NONE -> StaticImage(
            image = image,
            widthPx = widthPx,
            heightPx = heightPx,
            modifier = modifier,
            contentScale = contentScale,
        )

        ImageEffect.KEN_BURNS -> KenBurnsImage(
            image = image,
            durationMillis = durationMillis,
            widthPx = widthPx,
            heightPx = heightPx,
            modifier = modifier,
            contentScale = contentScale,
        )

        ImageEffect.GENTLE_DRIFT -> GentleDriftImage(
            image = image,
            durationMillis = durationMillis,
            widthPx = widthPx,
            heightPx = heightPx,
            modifier = modifier,
            contentScale = contentScale,
        )

        ImageEffect.PARALLAX_2_5D -> ParallaxImage(
            image = image,
            durationMillis = durationMillis,
            widthPx = widthPx,
            heightPx = heightPx,
            modifier = modifier,
        )

        ImageEffect.BREATHING -> BreathingImage(
            image = image,
            durationMillis = durationMillis,
            widthPx = widthPx,
            heightPx = heightPx,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

@Composable
private fun StaticImage(
    image: SourceImage,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(image.url)
            .size(Size(widthPx, heightPx))
            .crossfade(false)
            .build(),
        contentDescription = "Effect image ${image.id}",
        contentScale = contentScale,
        modifier = modifier,
    )
}

private data class KenBurnsSpec(
    val startScale: Float,
    val endScale: Float,
    val startX: Float,
    val endX: Float,
    val startY: Float,
    val endY: Float,
)

private fun kenBurnsSpecFor(id: Int): KenBurnsSpec {
    val zoomIn = id % 2 == 0
    val startScale = if (zoomIn) 1.10f else 1.22f
    val endScale = if (zoomIn) 1.22f else 1.10f
    return when (id % 4) {
        0 -> KenBurnsSpec(startScale, endScale, -0.02f, 0.02f, 0f, 0f)
        1 -> KenBurnsSpec(startScale, endScale, 0.02f, -0.02f, 0f, 0f)
        2 -> KenBurnsSpec(startScale, endScale, 0f, 0f, -0.02f, 0.02f)
        else -> KenBurnsSpec(startScale, endScale, 0f, 0f, 0.02f, -0.02f)
    }
}

@Composable
private fun KenBurnsImage(
    image: SourceImage,
    durationMillis: Int,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val animation = remember(image.id) { Animatable(0f) }
    val spec = remember(image.id) { kenBurnsSpecFor(image.id) }

    LaunchedEffect(image.id, durationMillis) {
        animation.snapTo(0f)
        animation.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis, easing = LinearEasing),
        )
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(image.url)
            .size(Size(widthPx, heightPx))
            .crossfade(false)
            .build(),
        contentDescription = "Effect image ${image.id}",
        contentScale = contentScale,
        modifier = modifier.graphicsLayer {
            val t = animation.value
            val scale = spec.startScale + (spec.endScale - spec.startScale) * t
            scaleX = scale
            scaleY = scale
            translationX = (spec.startX + (spec.endX - spec.startX) * t) * size.width
            translationY = (spec.startY + (spec.endY - spec.startY) * t) * size.height
        },
    )
}

private data class DriftSpec(
    val startX: Float,
    val endX: Float,
    val startY: Float,
    val endY: Float,
)

private fun driftSpecFor(id: Int): DriftSpec = when (id % 4) {
    0 -> DriftSpec(-0.025f, 0.025f, 0f, 0f)
    1 -> DriftSpec(0.025f, -0.025f, 0f, 0f)
    2 -> DriftSpec(0f, 0f, -0.025f, 0.025f)
    else -> DriftSpec(0f, 0f, 0.025f, -0.025f)
}

/** Pan-only: constant-speed glide, no zoom, so there is no resolution loss. */
@Composable
private fun GentleDriftImage(
    image: SourceImage,
    durationMillis: Int,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val animation = remember(image.id) { Animatable(0f) }
    val spec = remember(image.id) { driftSpecFor(image.id) }

    LaunchedEffect(image.id, durationMillis) {
        animation.snapTo(0f)
        animation.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis, easing = LinearEasing),
        )
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(image.url)
                .size(Size(widthPx, heightPx))
                .crossfade(false)
                .build(),
        contentDescription = "Effect image ${image.id}",
        contentScale = contentScale,
        modifier = modifier.graphicsLayer {
            val t = animation.value
            // Constant scale: the extra 10% is the room the pan travels through.
            scaleX = 1.1f
            scaleY = 1.1f
            translationX = (spec.startX + (spec.endX - spec.startX) * t) * size.width
            translationY = (spec.startY + (spec.endY - spec.startY) * t) * size.height
        },
    )
}

/** A slow sine "breath": zoom in and back out, so the motion never feels linear. */
@Composable
private fun BreathingImage(
    image: SourceImage,
    durationMillis: Int,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val animation = remember(image.id) { Animatable(0f) }

    LaunchedEffect(image.id, durationMillis) {
        animation.snapTo(0f)
        animation.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis, easing = LinearEasing),
        )
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(image.url)
            .size(Size(widthPx, heightPx))
            .crossfade(false)
            .build(),
        contentDescription = "Effect image ${image.id}",
        contentScale = contentScale,
        modifier = modifier.graphicsLayer {
            val t = animation.value
            // 1.05 -> 1.10 -> 1.05 across the shot (a full breath).
            val scale = 1.05f + 0.05f * (1f - cos(2 * PI * t).toFloat()) / 2f
            scaleX = scale
            scaleY = scale
        },
    )
}

/**
 * A first-pass 2.5D parallax: a blurred backdrop and the full image move at slightly different
 * speeds. This does not segment the subject; true subject isolation would use ML Kit Subject
 * Segmentation (currently a beta dependency).
 */
@Composable
private fun ParallaxImage(
    image: SourceImage,
    durationMillis: Int,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animation = remember(image.id) { Animatable(0f) }
    val model = remember(image.id, widthPx, heightPx) {
        ImageRequest.Builder(context)
            .data(image.url)
            .size(Size(widthPx, heightPx))
            .crossfade(false)
            .build()
    }

    LaunchedEffect(image.id, durationMillis) {
        animation.snapTo(0f)
        animation.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis, easing = LinearEasing),
        )
    }

    Box(modifier) {
        // Backdrop: blurred and moving slightly.
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = animation.value
                    scaleX = 1.12f + 0.04f * t
                    scaleY = 1.12f + 0.04f * t
                    translationX = (0.02f - 0.04f * t) * size.width
                    translationY = (0.01f - 0.02f * t) * size.height
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = BlurEffect(24f, 24f)
                    }
                },
        )
        // Foreground: the whole image, moving the other way.
        AsyncImage(
            model = model,
            contentDescription = "Effect image ${image.id}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = animation.value
                    scaleX = 1.02f
                    scaleY = 1.02f
                    translationX = (-0.015f + 0.03f * t) * size.width
                    translationY = (0.01f - 0.02f * t) * size.height
                },
        )
    }
}