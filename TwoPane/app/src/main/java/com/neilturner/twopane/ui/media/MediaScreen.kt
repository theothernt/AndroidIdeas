package com.neilturner.twopane.ui.media

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun MediaScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    if (isTv) {
        TvMediaScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = onBack
        )
    } else {
        MobileMediaScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = onBack
        )
    }
}
