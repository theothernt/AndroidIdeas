package com.neilturner.twopane

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neilturner.twopane.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uiState by viewModel.uiState.collectAsState()
    
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isLargeScreen = configuration.screenWidthDp >= 600

    if (isTv) {
        TvSettingsLayout(
            items = uiState.tvItems,
            selectedItem = uiState.selectedItem ?: uiState.tvItems.firstOrNull(),
            onItemSelect = { viewModel.selectItem(it) }
        )
    } else {
        MobileSettingsLayout(
            items = uiState.mobileItems,
            selectedItem = uiState.selectedItem,
            isTwoPane = isLandscape || isLargeScreen,
            onItemSelect = { viewModel.selectItem(it) },
            onBack = { viewModel.selectItem(null) }
        )
    }
}
