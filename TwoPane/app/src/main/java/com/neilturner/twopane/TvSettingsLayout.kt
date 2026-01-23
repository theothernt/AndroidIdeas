package com.neilturner.twopane

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.twopane.data.SettingItem
import com.neilturner.twopane.ui.theme.TwoPaneTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsLayout(
    items: List<SettingItem>,
    selectedItem: SettingItem?,
    isSubtitlesEnabled: Boolean,
    onItemSelect: (SettingItem) -> Unit,
    onToggleSubtitles: (Boolean) -> Unit
) {
    // Basic implementation matching the screenshot structure
    // Left: List of categories
    // Right: Content for the selected category
    
    Row(modifier = Modifier.fillMaxSize()) {
        // Left Pane
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
             items.forEach { item ->
                TvSettingsItem(
                    text = item.title,
                    icon = item.icon,
                    isSelected = selectedItem?.id == item.id,
                    onClick = { onItemSelect(item) }
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
            if (selectedItem?.id == "subtitles") {
                 SubtitlesSettingsContent(
                     isEnabled = isSubtitlesEnabled,
                     onToggle = onToggleSubtitles
                 )
            } else {
                 Text("Content for ${selectedItem?.title}")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsItem(
    text: String,
    icon: ImageVector?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
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
           if (icon != null) {
               Icon(
                   imageVector = icon,
                   contentDescription = null,
                   modifier = Modifier.padding(end = 12.dp)
               )
           }
           Text(text = text)
       }
   }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SubtitlesSettingsContent(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Subtitles Toggle
        androidx.tv.material3.Surface(
            onClick = { onToggle(!isEnabled) },
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
                    checked = isEnabled,
                    onCheckedChange = { onToggle(it) }
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

@Preview(device = "id:tv_1080p")
@Composable
fun TvSettingsPreview() {
    TwoPaneTheme {
        TvSettingsLayout(
            items = listOf(
                SettingItem("1", "Accounts", icon = Icons.Default.AccountCircle),
                SettingItem("2", "About", icon = Icons.Default.Info)
            ),
            selectedItem = SettingItem("1", "Accounts"),
            isSubtitlesEnabled = true,
            onItemSelect = {},
            onToggleSubtitles = {}
        )
    }
}