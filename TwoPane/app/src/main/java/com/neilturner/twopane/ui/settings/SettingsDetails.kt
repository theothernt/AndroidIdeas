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