package com.neilturner.twopane

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neilturner.twopane.data.SettingItem
import com.neilturner.twopane.ui.settings.SettingsDetailContent
import com.neilturner.twopane.ui.theme.TwoPaneTheme

@Composable
fun MobileSettingsLayout(
    items: List<SettingItem>,
    selectedItem: SettingItem?,
    isTwoPane: Boolean,
    onItemSelect: (SettingItem) -> Unit,
    onBack: () -> Unit
) {
    if (isTwoPane) {
        MobileTwoPaneLayout(
            items = items,
            selectedItem = selectedItem,
            onItemSelect = onItemSelect
        )
    } else {
        MobileSinglePaneLayout(
            items = items,
            selectedItem = selectedItem,
            onItemSelect = onItemSelect,
            onBack = onBack
        )
    }
}

@Composable
fun MobileTwoPaneLayout(
    items: List<SettingItem>,
    selectedItem: SettingItem?,
    onItemSelect: (SettingItem) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        // List Pane
        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.surface
        ) {
            SettingsList(
                items = items,
                onItemClick = onItemSelect,
                selectedItem = selectedItem
            )
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
    items: List<SettingItem>,
    selectedItem: SettingItem?,
    onItemSelect: (SettingItem) -> Unit,
    onBack: () -> Unit
) {
    if (selectedItem != null) {
        BackHandler(onBack = onBack)
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
             DetailContent(selectedItem)
        }
    } else {
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            SettingsList(
                items = items,
                onItemClick = onItemSelect,
                selectedItem = null
            )
        }
    }
}

@Composable
fun SettingsList(
    items: List<SettingItem>,
    onItemClick: (SettingItem) -> Unit,
    selectedItem: SettingItem?
) {
    LazyColumn {
        items(items) { item ->
            ListItem(
                headlineContent = { Text(item.title) },
                supportingContent = { Text(item.description) },
                leadingContent = if (item.icon != null) {
                    { Icon(imageVector = item.icon, contentDescription = null) }
                } else null,
                modifier = Modifier
                    .clickable { onItemClick(item) }
                    .then(
                        if (selectedItem?.id == item.id) 
                            Modifier.padding(start = 8.dp) 
                        else Modifier
                    )
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun DetailContent(selectedItem: SettingItem?) {
    SettingsDetailContent(selectedItem = selectedItem)
}

@Preview(showBackground = true, widthDp = 840)
@Composable
fun MobileTwoPanePreview() {
    TwoPaneTheme {
        MobileSettingsLayout(
            items = listOf(SettingItem("1", "Title", "Desc")),
            selectedItem = SettingItem("1", "Title", "Desc"),
            isTwoPane = true,
            onItemSelect = {},
            onBack = {}
        )
    }
}
