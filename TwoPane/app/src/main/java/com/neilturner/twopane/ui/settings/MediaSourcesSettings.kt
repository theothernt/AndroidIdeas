package com.neilturner.twopane.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

enum class MediaSourcesScreen {
    Main,
    Advanced
}

@Composable
fun MediaSourcesSettings() {
    var currentScreen by remember { mutableStateOf(MediaSourcesScreen.Main) }
    var shouldFocusAdvancedButton by remember { mutableStateOf(false) }

    BackHandler(enabled = currentScreen != MediaSourcesScreen.Main) {
        shouldFocusAdvancedButton = true
        currentScreen = MediaSourcesScreen.Main
    }

    when (currentScreen) {
        MediaSourcesScreen.Main -> {
            MediaSourcesMainScreen(
                shouldFocusAdvanced = shouldFocusAdvancedButton,
                onNavigateToAdvanced = { 
                    shouldFocusAdvancedButton = false
                    currentScreen = MediaSourcesScreen.Advanced 
                }
            )
        }
        MediaSourcesScreen.Advanced -> {
            MediaSourcesAdvancedScreen(onBack = { 
                shouldFocusAdvancedButton = true
                currentScreen = MediaSourcesScreen.Main 
            })
        }
    }
}

@Composable
fun MediaSourcesMainScreen(
    shouldFocusAdvanced: Boolean,
    onNavigateToAdvanced: () -> Unit
) {
    var appleEnabled by remember { mutableStateOf(false) }
    var amazonEnabled by remember { mutableStateOf(false) }
    var jetsonEnabled by remember { mutableStateOf(false) }
    val advancedButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(shouldFocusAdvanced) {
        if (shouldFocusAdvanced) {
            advancedButtonFocusRequester.requestFocus()
        }
    }

    Column(modifier = Modifier.padding(32.dp)) {
        Text("Media Sources", style = MaterialTheme.typography.headlineMedium)
        
        Column(
            modifier = Modifier.padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MediaSourceItem(
                title = "Apple videos",
                isEnabled = appleEnabled,
                onToggle = { appleEnabled = !appleEnabled }
            )
            MediaSourceItem(
                title = "Amazon videos",
                isEnabled = amazonEnabled,
                onToggle = { amazonEnabled = !amazonEnabled }
            )
            MediaSourceItem(
                title = "Jetson Creative videos",
                isEnabled = jetsonEnabled,
                onToggle = { jetsonEnabled = !jetsonEnabled }
            )
            
            MediaSourceNavigationItem(
                title = "Advanced Media Settings",
                onClick = onNavigateToAdvanced,
                modifier = Modifier
                    .focusRequester(advancedButtonFocusRequester)
                    .focusProperties { down = FocusRequester.Cancel }
            )
        }
    }
}

@Composable
fun MediaSourcesAdvancedScreen(onBack: () -> Unit) {
    var autoPlay by remember { mutableStateOf(true) }
    var showMetadata by remember { mutableStateOf(false) }
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstItemFocusRequester.requestFocus()
    }

    Column(modifier = Modifier.padding(32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.focusRequester(firstItemFocusRequester)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Advanced Settings", style = MaterialTheme.typography.headlineMedium)
        }
        
        Column(
            modifier = Modifier.padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MediaSourceItem(
                title = "Auto-play next video",
                isEnabled = autoPlay,
                onToggle = { autoPlay = !autoPlay }
            )
            MediaSourceItem(
                title = "Show media metadata",
                isEnabled = showMetadata,
                onToggle = { showMetadata = !showMetadata },
                modifier = Modifier.focusProperties { down = FocusRequester.Cancel }
            )
        }
    }
}

@Composable
fun MediaSourceItem(
    title: String,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
fun MediaSourceNavigationItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null
            )
        }
    }
}
