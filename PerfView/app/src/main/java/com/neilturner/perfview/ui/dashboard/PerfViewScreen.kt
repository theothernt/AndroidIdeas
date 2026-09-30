package com.neilturner.perfview.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neilturner.perfview.ui.components.TvActionButton
import com.neilturner.perfview.ui.components.TvPanelButtonWidth
import com.neilturner.perfview.ui.components.TvSecondaryButton
import com.neilturner.perfview.ui.dashboard.contract.DashboardContentState
import com.neilturner.perfview.ui.dashboard.contract.PerfViewViewState
import com.neilturner.perfview.ui.theme.PerfViewTheme
import com.neilturner.perfview.ui.theme.PerfViewTvTheme
import java.util.Locale

private val OverlayBackground = Color(0xF2102A36)
private val OverlayText = Color(0xFFEAF5F7)
private val OverlayShape = RoundedCornerShape(18.dp)

@Composable
fun PerfViewScreen(
    uiState: PerfViewViewState,
    onRunInBackground: () -> Unit,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val processLines = when (val content = uiState.dashboardState?.content) {
        is DashboardContentState.Data -> content.processes.take(5).mapIndexed { index, process ->
            "${index + 1}. ${String.format(Locale.US, "%.0f%%", process.cpuPercent)}  ${String.format(Locale.US, "%.0fMB", process.ramMb)}  ${process.name}"
        }

        is DashboardContentState.Loading -> listOf(content.message)
        is DashboardContentState.Empty -> listOf(content.message)
        is DashboardContentState.Unsupported -> listOf("ADB unavailable", content.message)
        null -> listOf("--", "--", "--", "--", "--")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07131F))
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OverlayBackground, OverlayShape)
                    .border(1.dp, Color(0xFF3A5A6A), OverlayShape)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(5) { index ->
                    Text(
                        text = processLines.getOrElse(index) { "--" },
                        color = OverlayText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TvActionButton(
                text = "Run in the background",
                onClick = onRunInBackground,
                width = TvPanelButtonWidth,
            )

            Spacer(modifier = Modifier.height(8.dp))

            TvSecondaryButton(
                text = "Exit app",
                onClick = onExitApp,
                width = TvPanelButtonWidth,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07131F)
@Composable
private fun PerfViewScreenPreview() {
    PerfViewTheme(dynamicColor = false) {
        PerfViewTvTheme {
            PerfViewScreen(
                uiState = PerfViewViewState(),
                onRunInBackground = {},
                onExitApp = {},
            )
        }
    }
}
