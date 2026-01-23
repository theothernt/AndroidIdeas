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
import com.neilturner.twopane.ui.settings.SettingsDetailContent
import com.neilturner.twopane.ui.theme.TwoPaneTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsLayout(
    items: List<SettingItem>,
    selectedItem: SettingItem?,
    onItemSelect: (SettingItem) -> Unit
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
        ) {
            // Content based on selection
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                SettingsDetailContent(selectedItem = selectedItem)
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
            onItemSelect = {}
        )
    }
}