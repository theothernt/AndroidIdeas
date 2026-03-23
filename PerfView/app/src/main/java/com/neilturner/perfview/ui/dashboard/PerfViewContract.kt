package com.neilturner.perfview.ui.dashboard

import android.content.Intent
import com.neilturner.perfview.data.cpu.TopProcessUsage

sealed interface PerfViewIntent {
    data object Load : PerfViewIntent
    data object RequestAdbAccess : PerfViewIntent
    data object RunInBackgroundClicked : PerfViewIntent
    data object OverlayPermissionResult : PerfViewIntent
    data object AppOpenedToForeground : PerfViewIntent
}

sealed interface PerfViewCommand {
    data class OpenOverlayPermissionSettings(
        val intent: Intent,
    ) : PerfViewCommand

    data object StartBackgroundOverlay : PerfViewCommand
    data object StopBackgroundOverlay : PerfViewCommand
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
    val backgroundActionMessage: String? = null,
)
