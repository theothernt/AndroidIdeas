package com.neilturner.perfview.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.perfview.data.cpu.TopProcessUsage
import com.neilturner.perfview.ui.theme.PerfViewTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun PerfViewRoute(
    viewModel: PerfViewViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.accept(PerfViewIntent.Load)
    }

    PerfViewScreen(uiState = uiState)
}

@Composable
fun PerfViewScreen(
    uiState: PerfViewViewState,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF07131F),
                        Color(0xFF0C2231),
                        Color(0xFF163A46),
                    )
                )
            )
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Header(uiState = uiState)
            ProcessListCard(
                processes = uiState.topProcesses,
                isSupported = uiState.isSupported,
                statusMessage = uiState.statusMessage,
            )
            if (!uiState.isSupported) {
                ErrorCard(message = uiState.statusMessage)
            }
        }
    }
}

@Composable
private fun Header(uiState: PerfViewViewState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Top CPU Processes",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (uiState.isLoading) {
                    "${uiState.sourceLabel} • ${uiState.statusMessage}"
                } else {
                    "${uiState.sourceLabel} • Last updated ${uiState.lastUpdatedLabel}"
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFD2E6E9),
            )
            PollingIndicator()
        }
    }
}

@Composable
private fun PollingIndicator() {
    val transition = rememberInfiniteTransition(label = "polling")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pollingRotation",
    )

    Box(
        modifier = Modifier
            .size(14.dp)
            .rotate(rotation),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(14.dp)) {
            drawArc(
                color = Color(0xFF52E3B0),
                startAngle = 0f,
                sweepAngle = 250f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun ProcessListCard(
    processes: List<TopProcessUsage>,
    isSupported: Boolean,
    statusMessage: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0x33224452),
                shape = RoundedCornerShape(22.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0x30D2E6E9),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (processes.isEmpty() || !isSupported) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFB1CDD2),
                )
            } else {
                ProcessHeaderRow()
                processes.take(10).forEachIndexed { index, process ->
                    ProcessRow(
                        rank = index + 1,
                        name = process.name,
                        cpuPercent = process.cpuPercent,
                        pid = process.pid,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeaderCell(text = "#", width = 34.dp)
        HeaderCell(text = "Process", width = 420.dp)
        HeaderCell(text = "CPU", width = 60.dp, alignEnd = true)
        HeaderCell(text = "PID", width = 64.dp, alignEnd = true)
    }
}

@Composable
private fun ProcessRow(
    rank: Int,
    name: String,
    cpuPercent: Float,
    pid: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CellText(text = rank.toString(), width = 34.dp, color = Color(0xFF8BE8FF))
        CellText(text = name, width = 420.dp, color = Color.White)
        CellText(text = String.format("%.1f%%", cpuPercent), width = 60.dp, color = Color(0xFF52E3B0), alignEnd = true)
        CellText(text = pid.toString(), width = 64.dp, color = Color(0xFFB1CDD2), alignEnd = true)
    }
}

@Composable
private fun HeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp? = null,
    alignEnd: Boolean = false,
) {
    CellText(
        text = text,
        width = width,
        color = Color(0xFF8FAAB1),
        alignEnd = alignEnd,
    )
}

@Composable
private fun CellText(
    text: String,
    width: androidx.compose.ui.unit.Dp? = null,
    color: Color,
    alignEnd: Boolean = false,
) {
    val modifier = if (width != null) Modifier.width(width) else Modifier

    Box(
        modifier = modifier,
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun ErrorCard(
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0x55A63939),
                shape = RoundedCornerShape(22.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0x50FFE2E2),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ADB connection unavailable",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFFFE2E2),
            )
            Text(
                text = "Perf View will retry automatically every 3 seconds.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFFFE2E2),
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun PerfViewScreenPreview() {
    PerfViewTheme {
        PerfViewScreen(
            uiState = PerfViewViewState(
                isLoading = false,
                topProcesses = listOf(
                    TopProcessUsage(1234, "com.android.systemui", 12.5f, "root", "S"),
                    TopProcessUsage(5678, "com.google.android.youtube", 8.2f, "u0_a123", "R"),
                    TopProcessUsage(9012, "com.neilturner.perfview", 5.1f, "u0_a456", "S"),
                    TopProcessUsage(3456, "system_server", 4.8f, "system", "S"),
                    TopProcessUsage(7890, "surfaceflinger", 3.2f, "system", "S"),
                ),
                isSupported = true,
                statusMessage = "Connected",
                lastUpdatedLabel = "12:34:56",
                sourceLabel = "ADB via Shell",
            )
        )
    }
}
