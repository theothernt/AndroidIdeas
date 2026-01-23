package com.neilturner.twopane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.rememberDrawerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsLayout() {
    // Basic implementation matching the screenshot structure
    // Left: List of categories
    // Right: Content for the selected category
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
    var selectedIndex by remember { mutableIntStateOf(0) }
    
    val items = listOf(
        "Accounts" to Icons.Default.AccountCircle,
        "About" to Icons.Default.Info,
        "Subtitles" to Icons.Default.Subtitles,
        "Language" to Icons.Default.Translate,
        "Search history" to Icons.Default.History,
        "Help and Support" to Icons.AutoMirrored.Filled.Help
    )

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Pane (Simulated with a column for now as per screenshot layout)
        // In a real TV app, NavigationDrawer is often used, but for this fixed 2-pane settings view, 
        // a Row with 2 columns is better if we want it always visible.
        
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
             items.forEachIndexed { index, item ->
                TvSettingsItem(
                    text = item.first,
                    icon = item.second,
                    isSelected = index == selectedIndex,
                    onClick = { selectedIndex = index }
                )
            }
        }

        // Right Pane
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(48.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Content based on selection
            when (selectedIndex) {
                2 -> SubtitlesSettingsContent() // Matches "Subtitles" index
                else -> Text("Content for ${items[selectedIndex].first}")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsItem(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Placeholder for a focusable TV item
    // In real implementation, use Surface or Button with focus interactions
   androidx.tv.material3.Surface(
        onClick = onClick,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.width(250.dp)
   ) {
       Row(
           modifier = Modifier.padding(12.dp),
           horizontalArrangement = Arrangement.Start,
           verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
       ) {
           Icon(
               imageVector = icon,
               contentDescription = null,
               modifier = Modifier.padding(end = 12.dp)
           )
           Text(text = text)
       }
   }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SubtitlesSettingsContent() {
    var subtitlesEnabled by remember { mutableStateOf(true) }
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Subtitles Toggle
        androidx.tv.material3.Surface(
            onClick = { subtitlesEnabled = !subtitlesEnabled },
            modifier = Modifier.width(400.dp),
             shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
             colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
             )
        ) {
            Row(
                 modifier = Modifier.fillMaxWidth().padding(16.dp),
                 horizontalArrangement = Arrangement.SpaceBetween,
                 verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Subtitles")
                androidx.tv.material3.Switch(
                    checked = subtitlesEnabled,
                    onCheckedChange = { subtitlesEnabled = it }
                )
            }
        }
        
        // Subtitles Language
        androidx.tv.material3.Surface(
            onClick = {}, // Open language picker
            modifier = Modifier.width(400.dp),
             shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
             colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
             )
        ) {
            Row(
                 modifier = Modifier.fillMaxWidth().padding(16.dp),
                 horizontalArrangement = Arrangement.SpaceBetween,
                 verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Subtitles Language")
                Text("English", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
