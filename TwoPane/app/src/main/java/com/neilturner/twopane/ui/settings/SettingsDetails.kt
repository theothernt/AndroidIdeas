package com.neilturner.twopane.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neilturner.twopane.data.SettingItem

@Composable
fun SettingsDetailContent(selectedItem: SettingItem?) {
    if (selectedItem == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a setting to view details")
        }
        return
    }

    when (selectedItem.id) {
        "media_sources" -> MediaSourcesSettings()
        "overlays" -> OverlaysSettings()
        "appearance" -> AppearanceSettings()
        "playlist" -> PlaylistSettings()
        "other" -> OtherSettings()
        else -> {
             Column(modifier = Modifier.padding(16.dp)) {
                Text(selectedItem.title, style = MaterialTheme.typography.headlineMedium)
                Text(selectedItem.description, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun MediaSourcesSettings() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Media Sources", style = MaterialTheme.typography.headlineMedium)
        Text("Manage your media libraries and sources here.", modifier = Modifier.padding(top = 8.dp))
        // Add specific controls here later
    }
}

@Composable
fun OverlaysSettings() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Overlays", style = MaterialTheme.typography.headlineMedium)
        Text("Configure on-screen overlays and widgets.", modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun AppearanceSettings() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Appearance", style = MaterialTheme.typography.headlineMedium)
        Text("Customize the look and feel of the application.", modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun PlaylistSettings() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Playlist", style = MaterialTheme.typography.headlineMedium)
        Text("Settings related to playlist management and playback order.", modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun OtherSettings() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Other", style = MaterialTheme.typography.headlineMedium)
        Text("Miscellaneous settings and advanced options.", modifier = Modifier.padding(top = 8.dp))
    }
}
