package com.neilturner.twopane.ui.mainmenu

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MainMenuScreen(
    viewModel: MainMenuViewModel = viewModel(),
    onNavigateToMedia: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val context = LocalContext.current
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    val items by viewModel.items.collectAsState()
    val selectedItemId by viewModel.selectedItemId.collectAsState()

    if (isTv) {
        TvMainMenu(
            items = items,
            selectedItemId = selectedItemId,
            modifier = Modifier.fillMaxSize(),
            onItemClick = { item ->
                viewModel.setSelectedItem(item.id)
                when (item.id) {
                    "media" -> onNavigateToMedia()
                    "settings" -> onNavigateToSettings()
                }
            }
        )
    } else {
        MobileMainMenu(
            items = items,
            modifier = Modifier.fillMaxSize(),
            onItemClick = { item ->
                viewModel.setSelectedItem(item.id)
                when (item.id) {
                    "media" -> onNavigateToMedia()
                    "settings" -> onNavigateToSettings()
                }
            }
        )
    }
}
