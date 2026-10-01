package com.neilturner.perfview.ui.dashboard.contract

sealed interface PerfViewCommand {
    data object StartBackgroundOverlay : PerfViewCommand
    data object ExitApp : PerfViewCommand
}
