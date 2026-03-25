package com.neilturner.perfview.ui.dashboard

import android.content.Intent
import com.neilturner.perfview.data.cpu.TopProcessUsage

sealed interface PerfViewIntent {
    data object Load : PerfViewIntent
    data object RequestAdbAccess : PerfViewIntent
    data object RunInBackgroundClicked : PerfViewIntent
    data object OverlayPermissionResult : PerfViewIntent
    data object AppOpenedToForeground : PerfViewIntent
    data object AppBackgrounded : PerfViewIntent
    data object ResumeObserving : PerfViewIntent
}

sealed interface PerfViewCommand {
    data class OpenOverlayPermissionSettings(
        val intent: Intent,
    ) : PerfViewCommand

    data object StartBackgroundOverlay : PerfViewCommand
    data object StopBackgroundOverlay : PerfViewCommand
}

enum class PermissionPhase {
    Rationale,
    Authorizing,
    Failed,
}

data class PerfViewViewState(
    val permissionState: PermissionUiState? = PermissionUiState(),
    val dashboardState: DashboardUiState? = null,
    val backgroundActionState: BackgroundActionUiState = BackgroundActionUiState(),
)

data class PermissionUiState(
    val phase: PermissionPhase = PermissionPhase.Rationale,
    val title: String = "ADB access is needed",
    val message: String =
        "Perf View uses Android's loopback ADB access to read live process CPU usage on this device.",
    val buttonLabel: String = "Grant ADB access",
    val detailMessage: String? = null,
)

data class DashboardUiState(
    val sourceLabel: String = "Starting",
    val statusLabel: String = "Starting process monitor",
    val lastUpdatedLabel: String? = null,
    val content: DashboardContentState = DashboardContentState.Loading(
        message = "Connecting to ADB and reading top process usage",
    ),
)

sealed interface DashboardContentState {
    data class Loading(
        val message: String,
    ) : DashboardContentState

    data class Data(
        val processes: List<TopProcessUsage>,
    ) : DashboardContentState

    data class Empty(
        val message: String,
    ) : DashboardContentState

    data class Unsupported(
        val message: String,
    ) : DashboardContentState
}

data class BackgroundActionUiState(
    val backgroundActionMessage: String? = null,
)
