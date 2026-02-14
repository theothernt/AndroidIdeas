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
) {
    val context = LocalContext.current
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    val items by viewModel.items.collectAsState()

    if (isTv) {
        TvMainMenu(
            items = items,
            modifier = Modifier.fillMaxSize(),
            onItemClick = { item ->
                if (item.id == "media") onNavigateToMedia()
            }
        )
    } else {
        MobileMainMenu(
            items = items,
            modifier = Modifier.fillMaxSize(),
            onItemClick = { item ->
                if (item.id == "media") onNavigateToMedia()
            }
        )
    }
}
