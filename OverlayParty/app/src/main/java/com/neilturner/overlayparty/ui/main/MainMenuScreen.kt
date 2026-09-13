package com.neilturner.overlayparty.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neilturner.overlayparty.ui.theme.OverlayPartyTheme

private const val BORDER_ALPHA_START = 0.8f
private const val BORDER_ALPHA_END = 0.2f
private val BORDER_BASE_COLOR = Color.White
private val BORDER_GAP = 6.dp
private val CORNER_RADIUS = 16.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainMenuScreen(
    onNavigateToScreenOne: () -> Unit,
    onNavigateToScreenTwo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val infiniteTransition = rememberInfiniteTransition(label = "border_transition")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = BORDER_ALPHA_START,
        targetValue = BORDER_ALPHA_END,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "border_alpha",
    )

    val animatedBorderColor = BORDER_BASE_COLOR.copy(alpha = animatedAlpha)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RectangleShape,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 64.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Overlay Party",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 48.dp),
            )

            Button(
                onClick = onNavigateToScreenOne,
                modifier =
                    Modifier
                        .size(width = 300.dp, height = 80.dp)
                        .focusRequester(focusRequester),
                glow =
                    ButtonDefaults.glow(
                        focusedGlow =
                            Glow(
                                elevationColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                elevation = 16.dp,
                            ),
                    ),
                shape =
                    ButtonDefaults.shape(
                        shape = RoundedCornerShape(CORNER_RADIUS),
                    ),
                border =
                    ButtonDefaults.border(
                        focusedBorder =
                            Border(
                                border = BorderStroke(width = 3.dp, color = animatedBorderColor),
                                inset = BORDER_GAP,
                                shape = RoundedCornerShape(BORDER_GAP + CORNER_RADIUS),
                            ),
                    ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Start Overlay Party",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onNavigateToScreenTwo,
                modifier = Modifier.size(width = 300.dp, height = 80.dp),
                glow =
                    ButtonDefaults.glow(
                        focusedGlow =
                            Glow(
                                elevationColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                elevation = 16.dp,
                            ),
                    ),
                shape =
                    ButtonDefaults.shape(
                        shape = RoundedCornerShape(CORNER_RADIUS),
                    ),
                border =
                    ButtonDefaults.border(
                        focusedBorder =
                            Border(
                                border = BorderStroke(width = 3.dp, color = animatedBorderColor),
                                inset = BORDER_GAP,
                                shape = RoundedCornerShape(BORDER_GAP + CORNER_RADIUS),
                            ),
                    ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Screen Two",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Preview(device = "id:tv_1080p")
@Composable
private fun MainMenuScreenPreview() {
    OverlayPartyTheme {
        MainMenuScreen(
            onNavigateToScreenOne = {},
            onNavigateToScreenTwo = {},
        )
    }
}
