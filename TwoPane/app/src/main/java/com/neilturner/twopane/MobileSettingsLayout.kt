package com.neilturner.twopane

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Simple data model for settings items
data class SettingItem(val title: String, val description: String)

val settingItems = listOf(
    SettingItem("Network & Internet", "Wi-Fi, Mobile, Data usage"),
    SettingItem("Connected devices", "Bluetooth, Cast"),
    SettingItem("Apps", "Recent apps, Default apps"),
    SettingItem("Notifications", "Notification history, Conversations"),
    SettingItem("Battery", "100%")
)

@Composable
fun MobileSettingsLayout(isTwoPane: Boolean) {
    var selectedItem by rememberSaveable { mutableStateOf<String?>(null) }

    if (isTwoPane) {
        MobileTwoPaneLayout(
            selectedItem = selectedItem,
            onItemSelect = { selectedItem = it }
        )
    } else {
        MobileSinglePaneLayout(
            selectedItem = selectedItem,
            onItemSelect = { selectedItem = it },
            onBack = { selectedItem = null }
        )
    }
}

@Composable
fun MobileTwoPaneLayout(
    selectedItem: String?,
    onItemSelect: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // List Pane
        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                 Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp)
                )
                SettingsList(
                    items = settingItems,
                    onItemClick = { onItemSelect(it.title) },
                    selectedItem = selectedItem
                )
            }
           
        }
        
        // Detail Pane
        Surface(
            modifier = Modifier.weight(2f),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            DetailContent(selectedItem)
        }
    }
}

@Composable
fun MobileSinglePaneLayout(
    selectedItem: String?,
    onItemSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    if (selectedItem != null) {
        BackHandler(onBack = onBack)
        Surface(modifier = Modifier.fillMaxSize()) {
             Column {
                 Text(
                     text = "Details", 
                     style = MaterialTheme.typography.headlineSmall,
                     modifier = Modifier.padding(16.dp)
                 )
                 DetailContent(selectedItem)
             }
        }
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {
             Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp)
                )
                SettingsList(
                    items = settingItems,
                    onItemClick = { onItemSelect(it.title) },
                    selectedItem = null
                )
            }
        }
    }
}

@Composable
fun SettingsList(
    items: List<SettingItem>,
    onItemClick: (SettingItem) -> Unit,
    selectedItem: String?
) {
    LazyColumn {
        items(items) { item ->
            ListItem(
                headlineContent = { Text(item.title) },
                supportingContent = { Text(item.description) },
                modifier = Modifier
                    .clickable { onItemClick(item) }
                    .then(
                        if (selectedItem == item.title) 
                            Modifier.padding(start = 8.dp) // Simple visual indicator
                        else Modifier
                    )
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun DetailContent(selectedItem: String?) {
    Column(modifier = Modifier.padding(24.dp)) {
        if (selectedItem != null) {
            Text(
                text = selectedItem,
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "Details for $selectedItem would go here.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
             Text(
                text = "Select an item to view details",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
