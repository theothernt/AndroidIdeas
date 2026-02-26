package com.neilturner.twopane.ui.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    if (isTv) {
        TvSettingsScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = onBack
        )
    } else {
        MobileSettingsScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = onBack
        )
    }
}
