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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neilturner.perfview.ui.dashboard.contract.DashboardContentState
import com.neilturner.perfview.ui.dashboard.contract.PerfViewViewState
import com.neilturner.perfview.ui.theme.PerfViewTheme

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
            "${index + 1}. ${String.format("%.0f%%", process.cpuPercent)}  ${String.format("%.0fMB", process.ramMb)}  ${process.name}"
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

            Button(
                onClick = onRunInBackground,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A8A5C),
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "Run in the background",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onExitApp,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Exit app",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF8AACB8),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07131F)
@Composable
private fun PerfViewScreenPreview() {
    PerfViewTheme(dynamicColor = false) {
        PerfViewScreen(
            uiState = PerfViewViewState(),
            onRunInBackground = {},
            onExitApp = {},
        )
    }
}
