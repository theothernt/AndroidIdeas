package com.neilturner.perfview.ui.dashboard

import com.neilturner.perfview.data.cpu.TopProcessUsage

sealed interface PerfViewIntent {
    data object Load : PerfViewIntent
    data object RequestAdbAccess : PerfViewIntent
}

enum class PerfViewScreen {
    PermissionRationale,
    Authorizing,
    AuthorizationFailed,
    Content,
}

data class PerfViewViewState(
    val screen: PerfViewScreen = PerfViewScreen.PermissionRationale,
    val isLoading: Boolean = true,
    val topProcesses: List<TopProcessUsage> = emptyList(),
    val isSupported: Boolean = true,
    val statusMessage: String = "Starting process monitor",
    val lastUpdatedLabel: String = "--:--:--",
    val sourceLabel: String = "Starting",
    val permissionTitle: String = "ADB access is needed",
    val permissionMessage: String =
        "Perf View uses Android's loopback ADB access to read live process CPU usage on this device.",
    val permissionButtonLabel: String = "Grant ADB access",
)
