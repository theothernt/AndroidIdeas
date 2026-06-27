package com.neilturner.perfview.ui.dashboard.contract

sealed interface PerfViewIntent {
    data object Load : PerfViewIntent
    data object RunInBackgroundClicked : PerfViewIntent
    data object OverlayPermissionResult : PerfViewIntent
    data object ExitApp : PerfViewIntent
}
