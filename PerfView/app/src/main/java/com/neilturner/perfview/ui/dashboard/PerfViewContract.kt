package com.neilturner.perfview.ui.dashboard

import com.neilturner.perfview.data.cpu.TopProcessUsage

sealed interface PerfViewIntent {
    data object Load : PerfViewIntent
}

data class PerfViewViewState(
    val isLoading: Boolean = true,
    val topProcesses: List<TopProcessUsage> = emptyList(),
    val isSupported: Boolean = true,
    val statusMessage: String = "Starting process monitor",
    val lastUpdatedLabel: String = "--:--:--",
    val sourceLabel: String = "Starting",
)
