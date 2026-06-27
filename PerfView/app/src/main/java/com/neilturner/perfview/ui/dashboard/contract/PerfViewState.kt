package com.neilturner.perfview.ui.dashboard.contract

import androidx.compose.runtime.Stable
import com.neilturner.perfview.data.cpu.model.TopProcessUsage

@Stable
data class PerfViewViewState(
    val dashboardState: DashboardUiState? = null,
    val backgroundActionState: BackgroundActionUiState = BackgroundActionUiState(),
)

@Stable
data class DashboardUiState(
    val sourceLabel: String = "Starting",
    val statusLabel: String = "Starting process monitor",
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
