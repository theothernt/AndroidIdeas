package com.neilturner.twopane

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isLargeScreen = configuration.screenWidthDp >= 600

    if (isTv) {
        TvSettingsLayout()
    } else {
        MobileSettingsLayout(
            isTwoPane = isLandscape || isLargeScreen
        )
    }
}
