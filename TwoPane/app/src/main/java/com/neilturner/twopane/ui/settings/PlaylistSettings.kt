package com.neilturner.twopane.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlaylistSettings() {
    var defaultPlaylistName by remember { mutableStateOf("My Favorites") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Playlist", style = MaterialTheme.typography.headlineMedium)
        Text("Settings related to playlist management and playback order.", modifier = Modifier.padding(top = 8.dp))
        
        OutlinedTextField(
            value = defaultPlaylistName,
            onValueChange = { defaultPlaylistName = it },
            label = { Text("Default Playlist Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            singleLine = true
        )
    }
}