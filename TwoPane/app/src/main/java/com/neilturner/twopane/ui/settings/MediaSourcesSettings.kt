package com.neilturner.twopane.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaSourcesSettings() {
    var appleEnabled by remember { mutableStateOf(false) }
    var amazonEnabled by remember { mutableStateOf(false) }
    var jetsonEnabled by remember { mutableStateOf(false) }

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
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaSourceItem(
    title: String,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier.fillMaxWidth()
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
            androidx.compose.material3.Switch(
                checked = isEnabled,
                onCheckedChange = null // Handled by Surface onClick
            )
        }
    }
}
