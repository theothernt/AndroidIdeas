package com.neilturner.visualmashup.ui.screens.menu

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neilturner.visualmashup.R
import com.neilturner.visualmashup.data.ImageOrientation
import com.neilturner.visualmashup.data.ImageSource
import com.neilturner.visualmashup.data.SourceImage
import com.neilturner.visualmashup.data.maxImageSize
import com.neilturner.visualmashup.ui.components.OptionDialog
import com.neilturner.visualmashup.ui.effects.EffectSettings
import com.neilturner.visualmashup.ui.effects.EffectSpeed
import com.neilturner.visualmashup.ui.effects.ImageEffect
import com.neilturner.visualmashup.ui.effects.Transition
import com.neilturner.visualmashup.ui.effects.TransitionSettings
import com.neilturner.visualmashup.ui.theme.VisualMashupTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

private val CARD_WIDTH = 300.dp
private val CARD_HEIGHT = 180.dp
private val CARD_SPACING = 24.dp
private val BACKGROUND_INTERVAL_MS = 15_000L
private val BACKGROUND_CROSSFADE_MS = 4_000
private val CARD_CORNER = 16.dp
private const val FOCUS_ANIMATION_MS = 160
private const val FOCUS_SCALE_RANGE = 0.05f
private val CARD_SHAPE = RoundedCornerShape(CARD_CORNER)

/**
 * The app's entry screen: a dimmed, slowly crossfading photo background behind a hero title, three
 * image-led primary actions, and a quiet row of settings chips.
 *
 * The first card is focused on first composition so the D-pad has a clear starting point.
 */
@Composable
fun MenuScreen(
    onNavigateToSimpleCollage: () -> Unit,
    onNavigateToScrollingCollage: () -> Unit,
    onNavigateToEffects: () -> Unit,
    focusRequest: Int = 0,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val scrollingCardFocus = remember { FocusRequester() }
    val effectsCardFocus = remember { FocusRequester() }
    val speedButtonFocus = remember { FocusRequester() }
    val transitionButtonFocus = remember { FocusRequester() }
    val imageEffectButtonFocus = remember { FocusRequester() }
    var lastFocused by rememberSaveable { mutableIntStateOf(0) }
    val speed by EffectSettings.speed.collectAsStateWithLifecycle()
    val transition by TransitionSettings.transition.collectAsStateWithLifecycle()
    val effect by EffectSettings.effect.collectAsStateWithLifecycle()
    var speedDialogVisible by remember { mutableStateOf(false) }
    var transitionDialogVisible by remember { mutableStateOf(false) }
    var imageEffectDialogVisible by remember { mutableStateOf(false) }
    var refocusSpeed by remember { mutableStateOf(false) }
    var refocusTransition by remember { mutableStateOf(false) }
    var refocusImageEffect by remember { mutableStateOf(false) }
    // Bumped by every focus change so the background only crossfades after the menu has been idle.
    val interactions = remember { mutableIntStateOf(0) }

    val images = remember { ImageSource.images }
    val collageImage = remember { images.first { it.orientation == ImageOrientation.SQUARE } }
    val scrollingImage = remember { images.first { it.orientation == ImageOrientation.PORTRAIT } }
    val effectsImage = remember { images.first { it.orientation == ImageOrientation.LANDSCAPE } }

    // Restores the last used item on first composition and whenever the back stack returns.
    LaunchedEffect(focusRequest) {
        val target = when (lastFocused) {
            1 -> scrollingCardFocus
            2 -> effectsCardFocus
            3 -> imageEffectButtonFocus
            4 -> speedButtonFocus
            5 -> transitionButtonFocus
            else -> focusRequester
        }
        target.requestFocus()
    }

    LaunchedEffect(refocusSpeed) {
        if (refocusSpeed) {
            speedButtonFocus.requestFocus()
            refocusSpeed = false
        }
    }

    LaunchedEffect(refocusTransition) {
        if (refocusTransition) {
            transitionButtonFocus.requestFocus()
            refocusTransition = false
        }
    }

    LaunchedEffect(refocusImageEffect) {
        if (refocusImageEffect) {
            imageEffectButtonFocus.requestFocus()
            refocusImageEffect = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        MenuBackground(interactions = interactions, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 64.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.menu_title),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Turn your photos into a living collage",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(CARD_SPACING)) {
                ActionCard(
                    title = stringResource(R.string.simple_collage),
                    subtitle = "Full-bleed layouts",
                    image = collageImage,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                lastFocused = 0
                                interactions.intValue++
                            }
                        },
                    onClick = onNavigateToSimpleCollage,
                )
                ActionCard(
                    title = stringResource(R.string.scrolling_collage),
                    subtitle = "Endless auto-scroll",
                    image = scrollingImage,
                    modifier = Modifier
                        .focusRequester(scrollingCardFocus)
                        .onFocusChanged {
                            if (it.isFocused) {
                                lastFocused = 1
                                interactions.intValue++
                            }
                        },
                    onClick = onNavigateToScrollingCollage,
                )
                ActionCard(
                    title = stringResource(R.string.image_effects),
                    subtitle = "Ken Burns & transitions",
                    image = effectsImage,
                    modifier = Modifier
                        .focusRequester(effectsCardFocus)
                        .onFocusChanged {
                            if (it.isFocused) {
                                lastFocused = 2
                                interactions.intValue++
                            }
                        },
                    onClick = onNavigateToEffects,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { imageEffectDialogVisible = true },
                    modifier = Modifier
                        .focusRequester(imageEffectButtonFocus)
                        .onFocusChanged {
                            if (it.isFocused) {
                                lastFocused = 3
                                interactions.intValue++
                            }
                        },
                ) {
                    Text(text = "${stringResource(R.string.image_effect)}: ${effect.displayName}")
                }
                Button(
                    onClick = { speedDialogVisible = true },
                    modifier = Modifier
                        .focusRequester(speedButtonFocus)
                        .onFocusChanged {
                            if (it.isFocused) {
                                lastFocused = 4
                                interactions.intValue++
                            }
                        },
                ) {
                    Text(text = "${stringResource(R.string.speed)}: ${speed.displayName}")
                }
                Button(
                    onClick = { transitionDialogVisible = true },
                    modifier = Modifier
                        .focusRequester(transitionButtonFocus)
                        .onFocusChanged {
                            if (it.isFocused) {
                                lastFocused = 5
                                interactions.intValue++
                            }
                        },
                ) {
                    Text(text = "${stringResource(R.string.transition)}: ${transition.displayName}")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "D-pad to navigate  •  OK to select",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }

    if (speedDialogVisible) {
        Dialog(
            onDismissRequest = {
                speedDialogVisible = false
                refocusSpeed = true
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            OptionDialog(
                title = "Effect speed",
                subtitle = "Choose how fast the Ken Burns effect plays.",
                options = EffectSpeed.entries.map { it.displayName },
                selectedIndex = EffectSpeed.entries.indexOf(speed),
                onSelect = { index ->
                    EffectSettings.speed.value = EffectSpeed.entries[index]
                    speedDialogVisible = false
                    refocusSpeed = true
                },
            )
        }
    }

    if (imageEffectDialogVisible) {
        Dialog(
            onDismissRequest = {
                imageEffectDialogVisible = false
                refocusImageEffect = true
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            OptionDialog(
                title = "Image effect",
                subtitle = "Choose what happens on the image while it is shown.",
                options = ImageEffect.entries.map { it.displayName },
                selectedIndex = ImageEffect.entries.indexOf(effect),
                onSelect = { index ->
                    EffectSettings.effect.value = ImageEffect.entries[index]
                    imageEffectDialogVisible = false
                    refocusImageEffect = true
                },
            )
        }
    }

    if (transitionDialogVisible) {
        Dialog(
            onDismissRequest = {
                transitionDialogVisible = false
                refocusTransition = true
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            OptionDialog(
                title = "Transition",
                subtitle = "Choose how one image changes into the next.",
                options = Transition.entries.map { it.displayName },
                selectedIndex = Transition.entries.indexOf(transition),
                onSelect = { index ->
                    TransitionSettings.transition.value = Transition.entries[index]
                    transitionDialogVisible = false
                    refocusTransition = true
                },
            )
        }
    }
}

/**
 * A dimmed photo background that crossfades slowly, but only after the menu has been idle.
 *
 * Every focus change bumps [interactions], which restarts the timer, so the expensive full-screen
 * crossfade never competes with D-pad navigation. The tick is observed in a coroutine rather than
 * in composition, so focus changes don't recompose the menu.
 */
@Composable
private fun MenuBackground(
    interactions: MutableIntState,
    modifier: Modifier = Modifier,
) {
    val images = remember { ImageSource.images.shuffled(Random(11)).take(6) }
    var index by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            val interacted = withTimeoutOrNull(BACKGROUND_INTERVAL_MS) {
                snapshotFlow { interactions.intValue }.drop(1).first()
            }
            if (interacted == null) {
                index = (index + 1) % images.size
            }
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        // Keep the photo and its scrim in one cached layer. Focus changes elsewhere in the menu
        // then composite a single texture instead of redrawing every full-screen layer.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            Crossfade(
                targetState = index,
                animationSpec = tween(BACKGROUND_CROSSFADE_MS),
                modifier = Modifier.fillMaxSize(),
            ) { imageIndex ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(images[imageIndex].url)
                        .size(maxImageSize(480, 270))
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // One scrim (not two full-screen gradients) keeps the title and cards legible while
            // removing a full-screen pass from every background redraw.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.15f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.92f),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    image: SourceImage,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    // State read only in draw/layer lambdas: focus changes never recompose the photo or text.
    val focused = interactionSource.collectIsFocusedAsState()

    // Drive the focus animation from a coroutine, so the card is not recomposed per frame. Scale
    // and ring alpha are layer properties, so the animation also never re-records the photo.
    val focusProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { focused.value }.collectLatest { isFocused ->
            focusProgress.animateTo(
                targetValue = if (isFocused) 1f else 0f,
                animationSpec = tween(FOCUS_ANIMATION_MS),
            )
        }
    }

    val widthPx = with(density) { CARD_WIDTH.toPx() }.toInt()
    val heightPx = with(density) { CARD_HEIGHT.toPx() }.toInt()

    Box(
        modifier = modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .graphicsLayer {
                val scale = 1f + FOCUS_SCALE_RANGE * focusProgress.value
                scaleX = scale
                scaleY = scale
            }
            .clip(CARD_SHAPE)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(image.url)
                .size(maxImageSize(widthPx, heightPx))
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.85f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }

        // Static rings: focus only animates the white ring's layer alpha, never its drawing.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val ringWidth = 1.dp.toPx()
                    val inset = ringWidth / 2f
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.25f),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - ringWidth, size.height - ringWidth),
                        cornerRadius = CornerRadius(CARD_CORNER.toPx()),
                        style = Stroke(width = ringWidth),
                    )
                },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = focusProgress.value }
                .drawBehind {
                    val ringWidth = 3.dp.toPx()
                    val inset = ringWidth / 2f
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - ringWidth, size.height - ringWidth),
                        cornerRadius = CornerRadius(CARD_CORNER.toPx()),
                        style = Stroke(width = ringWidth),
                    )
                },
        )
    }
}

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun MenuScreenPreview() {
    VisualMashupTheme {
        MenuScreen(
            onNavigateToSimpleCollage = {},
            onNavigateToScrollingCollage = {},
            onNavigateToEffects = {},
        )
    }
}