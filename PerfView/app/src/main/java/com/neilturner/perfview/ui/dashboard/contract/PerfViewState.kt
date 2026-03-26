package com.neilturner.perfview.ui.dashboard.contract

import androidx.compose.runtime.Stable
import com.neilturner.perfview.data.cpu.model.TopProcessUsage

@Stable
data class PerfViewViewState(
    val permissionState: PermissionUiState? = PermissionUiState(),
    val dashboardState: DashboardUiState? = null,
    val backgroundActionState: BackgroundActionUiState = BackgroundActionUiState(),
)

@Stable
data class PermissionUiState(
    val phase: PermissionPhase = PermissionPhase.Rationale,
    val title: String = "ADB access is needed",
    val message: String =
        "Perf View uses Android's loopback ADB access to read live process CPU usage on this device.",
    val buttonLabel: String = "Grant ADB access",
    val detailMessage: String? = null,
)

enum class PermissionPhase {
    Rationale,
    Authorizing,
    Failed,
}

@Stable
data class DashboardUiState(
    val sourceLabel: String = "Starting",
    val statusLabel: String = "Starting process monitor",
    val lastUpdatedLabel: String? = null,
    val isPolling: Boolean = false,
    val content: DashboardContentState = DashboardContentState.Loading(
        message = "Connecting to ADB and reading top process usage",
    ),
)

@Stable
sealed interface DashboardContentState {
    @Stable
    data class Loading(
        val message: String,
    ) : DashboardContentState

    @Stable
    data class Data(
        val processes: List<TopProcessUsage>,
    ) : DashboardContentState

    @Stable
    data class Empty(
        val message: String,
    ) : DashboardContentState

    @Stable
    data class Unsupported(
        val message: String,
    ) : DashboardContentState
}

@Stable
data class BackgroundActionUiState(
    val backgroundActionMessage: String? = null,
)
