package com.neilturner.perfview.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.neilturner.perfview.data.cpu.TopProcessUsage
import com.neilturner.perfview.ui.theme.PerfViewTheme
import com.neilturner.perfview.ui.theme.PerfViewTokens

@Composable
fun PerfViewScreen(
    uiState: PerfViewViewState,
    onRequestAdbAccess: () -> Unit = {},
    onRunInBackground: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PerfViewTokens.panelBackgroundBrush)
            .padding(
                horizontal = PerfViewTokens.screenHorizontalPadding,
                vertical = PerfViewTokens.screenVerticalPadding,
            )
    ) {
        when (uiState.screen) {
            PerfViewScreen.PermissionRationale,
            PerfViewScreen.Authorizing,
            PerfViewScreen.AuthorizationFailed -> {
                PermissionGateCard(
                    uiState = uiState,
                    onRequestAdbAccess = onRequestAdbAccess,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            PerfViewScreen.Content -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(PerfViewTokens.sectionSpacing),
                ) {
                    PerfHeaderCard(uiState = uiState)
                    ProcessListCard(
                        processes = uiState.topProcesses,
                        isSupported = uiState.isSupported,
                        statusMessage = uiState.statusMessage,
                    )
                    if (!uiState.isSupported) {
                        ErrorCard(message = uiState.statusMessage)
                    }
                }
                BackgroundActionCard(
                    message = uiState.backgroundActionMessage,
                    onRunInBackground = onRunInBackground,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
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
                screen = PerfViewScreen.Content,
                isLoading = false,
                topProcesses = listOf(
                    TopProcessUsage(pid = 1178, name = "system_server", cpuPercent = 38f, ramPercent = 7.8f, ramMb = 812f, user = "system", state = "R"),
                    TopProcessUsage(pid = 9842, name = "com.example.app", cpuPercent = 24f, ramPercent = 3.1f, ramMb = 315f, user = "u0_a152", state = "S"),
                    TopProcessUsage(pid = 602, name = "surfaceflinger", cpuPercent = 12f, ramPercent = 1.6f, ramMb = 164f, user = "system", state = "S"),
                ),
                lastUpdatedLabel = "22:41:10",
                sourceLabel = "ADB shell",
                statusMessage = "Top process usage via ADB",
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
                screen = PerfViewScreen.PermissionRationale,
                isLoading = false,
                permissionTitle = "Allow loopback ADB access",
                permissionMessage = "Grant access, then approve the system debugging prompt.",
            )
        )
    }
}
