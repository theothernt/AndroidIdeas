package com.neilturner.perfview.ui.dashboard.contract

import android.content.Intent

sealed interface PerfViewCommand {
    data class OpenOverlayPermissionSettings(
        val intent: Intent,
    ) : PerfViewCommand

    data object StartBackgroundOverlay : PerfViewCommand
    data object StopBackgroundOverlay : PerfViewCommand
}
