package com.neilturner.visualmashup.ui.screens.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.visualmashup.R
import com.neilturner.visualmashup.ui.components.OptionDialog
import com.neilturner.visualmashup.ui.effects.EffectSettings
import com.neilturner.visualmashup.ui.effects.EffectSpeed
import com.neilturner.visualmashup.ui.effects.ImageEffect
import com.neilturner.visualmashup.ui.effects.Transition
import com.neilturner.visualmashup.ui.effects.TransitionSettings
import com.neilturner.visualmashup.ui.theme.VisualMashupTheme

/**
 * The app's entry screen. Shows the title, the main action (Simple Collage on the first row,
 * the related Effects/Speed/Transition group on the second), and modal pickers.
 *
 * The first button is focused on first composition so the D-pad has a clear starting point.
 */
@Composable
fun MenuScreen(
    onNavigateToSimpleCollage: () -> Unit,
    onNavigateToScrollingCollage: () -> Unit,
    onNavigateToEffects: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val speedButtonFocus = remember { FocusRequester() }
    val transitionButtonFocus = remember { FocusRequester() }
    val imageEffectButtonFocus = remember { FocusRequester() }
    val speed by EffectSettings.speed.collectAsStateWithLifecycle()
    val transition by TransitionSettings.transition.collectAsStateWithLifecycle()
    val effect by EffectSettings.effect.collectAsStateWithLifecycle()
    var speedDialogVisible by remember { mutableStateOf(false) }
    var transitionDialogVisible by remember { mutableStateOf(false) }
    var imageEffectDialogVisible by remember { mutableStateOf(false) }
    var refocusSpeed by remember { mutableStateOf(false) }
    var refocusTransition by remember { mutableStateOf(false) }
    var refocusImageEffect by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.menu_title),
            style = MaterialTheme.typography.displayMedium,
        )
        Spacer(modifier = Modifier.height(32.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onNavigateToSimpleCollage,
                    modifier = Modifier.focusRequester(focusRequester),
                ) {
                    Text(text = stringResource(R.string.simple_collage))
                }
                Button(onClick = onNavigateToScrollingCollage) {
                    Text(text = stringResource(R.string.scrolling_collage))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onNavigateToEffects) {
                    Text(text = stringResource(R.string.effects))
                }
                Button(
                    onClick = { imageEffectDialogVisible = true },
                    modifier = Modifier.focusRequester(imageEffectButtonFocus),
                ) {
                    Text(text = "${stringResource(R.string.image_effect)}: ${effect.displayName}")
                }
                Button(
                    onClick = { speedDialogVisible = true },
                    modifier = Modifier.focusRequester(speedButtonFocus),
                ) {
                    Text(text = "${stringResource(R.string.speed)}: ${speed.displayName}")
                }
                Button(
                    onClick = { transitionDialogVisible = true },
                    modifier = Modifier.focusRequester(transitionButtonFocus),
                ) {
                    Text(text = "${stringResource(R.string.transition)}: ${transition.displayName}")
                }
            }
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