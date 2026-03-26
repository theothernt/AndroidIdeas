package com.neilturner.perfview.ui.dashboard.contract

sealed interface PerfViewIntent {
    data object Load : PerfViewIntent
    data object RequestAdbAccess : PerfViewIntent
    data object RunInBackgroundClicked : PerfViewIntent
    data object OverlayPermissionResult : PerfViewIntent
    data object AppOpenedToForeground : PerfViewIntent
    data object AppBackgrounded : PerfViewIntent
    data object ResumeObserving : PerfViewIntent
}
