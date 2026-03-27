package com.neilturner.perfview.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.neilturner.perfview.data.cpu.model.TopProcessUsage
import com.neilturner.perfview.ui.dashboard.components.BackgroundActionCard
import com.neilturner.perfview.ui.dashboard.components.ErrorCard
import com.neilturner.perfview.ui.dashboard.components.PermissionGateCard
import com.neilturner.perfview.ui.dashboard.components.PerfHeaderCard
import com.neilturner.perfview.ui.dashboard.components.ProcessListCard
import com.neilturner.perfview.ui.dashboard.contract.DashboardContentState
import com.neilturner.perfview.ui.dashboard.contract.DashboardUiState
import com.neilturner.perfview.ui.dashboard.contract.PermissionPhase
import com.neilturner.perfview.ui.dashboard.contract.PermissionUiState
import com.neilturner.perfview.ui.dashboard.contract.PerfViewViewState
import com.neilturner.perfview.ui.theme.PerfViewTheme
import com.neilturner.perfview.ui.theme.PerfViewTokens

@Composable
fun PerfViewScreen(
    uiState: PerfViewViewState,
    onRequestAdbAccess: () -> Unit = {},
    onRunInBackground: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val permissionState = uiState.permissionState
    val dashboardState = uiState.dashboardState

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PerfViewTokens.panelBackgroundBrush)
            .padding(
                horizontal = PerfViewTokens.screenHorizontalPadding,
                vertical = PerfViewTokens.screenVerticalPadding,
            )
    ) {
        if (permissionState != null) {
            PermissionGateCard(
                permissionState = permissionState,
                onRequestAdbAccess = onRequestAdbAccess,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (dashboardState != null) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(PerfViewTokens.sectionSpacing),
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.18f)
                        .fillMaxHeight()
                        .widthIn(min = PerfViewTokens.statusColumnMinWidth),
                    verticalArrangement = Arrangement.spacedBy(PerfViewTokens.sectionSpacing),
                ) {
                    PerfHeaderCard(
                        dashboardState = dashboardState,
                        modifier = Modifier.weight(1f, fill = true),
                    )
                    BackgroundActionCard(
                        backgroundActionState = uiState.backgroundActionState,
                        onRunInBackground = onRunInBackground,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(0.82f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(PerfViewTokens.sectionSpacing),
                )
                {
                    ProcessListCard(
                        contentState = dashboardState.content,
                        modifier = Modifier.weight(1f, fill = true),
                    )
                    if (dashboardState.content is DashboardContentState.Unsupported) {
                        ErrorCard(message = dashboardState.content.message)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07131F)
@Composable
private fun PerfViewScreenContentPreview() {
    PerfViewTheme(dynamicColor = false) {
        PerfViewScreen(
            uiState = PerfViewViewState(
                permissionState = null,
                dashboardState = DashboardUiState(
                    sourceLabel = "ADB shell",
                    statusLabel = "Top process usage via ADB",
                    lastUpdatedLabel = "22:41:10",
                    content = DashboardContentState.Data(
                        processes = listOf(
                            TopProcessUsage(pid = 1178, name = "system_server", cpuPercent = 38f, ramPercent = 7.8f, ramMb = 812f, user = "system", state = "R"),
                            TopProcessUsage(pid = 9842, name = "com.example.app", cpuPercent = 24f, ramPercent = 3.1f, ramMb = 315f, user = "u0_a152", state = "S"),
                            TopProcessUsage(pid = 602, name = "surfaceflinger", cpuPercent = 12f, ramPercent = 1.6f, ramMb = 164f, user = "system", state = "S"),
                        ),
                    ),
                ),
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07131F)
@Composable
private fun PerfViewScreenPermissionPreview() {
    PerfViewTheme(dynamicColor = false) {
        PerfViewScreen(
            uiState = PerfViewViewState(
                permissionState = PermissionUiState(
                    phase = PermissionPhase.Rationale,
                    title = "Allow loopback ADB access",
                    message = "Grant access, then approve the system debugging prompt.",
                ),
            )
        )
    }
}
