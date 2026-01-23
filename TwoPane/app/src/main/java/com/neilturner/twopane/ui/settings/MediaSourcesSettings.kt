package com.neilturner.twopane.ui.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun MediaSourcesSettings() {
    var localLibraryEnabled by remember { mutableStateOf(true) }
    var networkStorageEnabled by remember { mutableStateOf(false) }
    var contextMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Media Sources", style = MaterialTheme.typography.headlineMedium)
        Text("Manage your media libraries and sources here.", modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { contextMenuExpanded = true }
                        )
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Local Storage (Long press for options)", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Local Library", modifier = Modifier.weight(1f))
                        Switch(
                            checked = localLibraryEnabled,
                            onCheckedChange = { localLibraryEnabled = it }
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = contextMenuExpanded,
                onDismissRequest = { contextMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename Source") },
                    onClick = { contextMenuExpanded = false },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Properties") },
                    onClick = { contextMenuExpanded = false },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Network Storage", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable NAS / SMB", modifier = Modifier.weight(1f))
                    Switch(
                        checked = networkStorageEnabled,
                        onCheckedChange = { networkStorageEnabled = it }
                    )
                }
                
                if (networkStorageEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Add Network Share")
                    }
                }
            }
        }

        OutlinedButton(onClick = { /* TODO */ }) {
            Text("Rescan All Sources")
        }
    }
}
