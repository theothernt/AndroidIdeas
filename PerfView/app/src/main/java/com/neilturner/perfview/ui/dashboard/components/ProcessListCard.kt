package com.neilturner.perfview.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neilturner.perfview.data.cpu.model.TopProcessUsage
import com.neilturner.perfview.ui.dashboard.contract.DashboardContentState
import com.neilturner.perfview.ui.theme.PerfAmber
import com.neilturner.perfview.ui.theme.PerfSky
import com.neilturner.perfview.ui.theme.PerfViewTokens

@Composable
fun ProcessListCard(
    contentState: DashboardContentState,
    modifier: Modifier = Modifier,
) {
    DashboardCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PerfViewTokens.cardSpacing),
        ) {
            when (contentState) {
                is DashboardContentState.Loading,
                is DashboardContentState.Empty,
                is DashboardContentState.Unsupported -> {
                    Text(
                        text = when (contentState) {
                            is DashboardContentState.Loading -> contentState.message
                            is DashboardContentState.Empty -> contentState.message
                            is DashboardContentState.Unsupported -> contentState.message
                            is DashboardContentState.Data -> error("Unreachable")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is DashboardContentState.Data -> {
                    ProcessHeaderRow()
                    contentState.processes.take(10).forEachIndexed { index, process ->
                        ProcessRow(
                            rank = index + 1,
                            cpuPercent = process.cpuPercent,
                            ramMb = process.ramMb,
                            name = process.name,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeaderCell(text = "#", width = 28.dp)
        HeaderCell(text = "CPU", width = 56.dp, alignEnd = true)
        HeaderCell(text = "RAM", width = 64.dp, alignEnd = true)
        HeaderCell(text = "Process")
    }
}

@Composable
private fun ProcessRow(
    rank: Int,
    cpuPercent: Float,
    ramMb: Float,
    name: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CellText(text = rank.toString(), width = 28.dp, color = PerfSky)
        CellText(
            text = String.format("%.0f%%", cpuPercent),
            width = 56.dp,
            color = MaterialTheme.colorScheme.secondary,
            alignEnd = true,
        )
        CellText(
            text = String.format("%.0fMB", ramMb),
            width = 64.dp,
            color = PerfAmber,
            alignEnd = true,
        )
        CellText(
            text = name,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HeaderCell(
    text: String,
    width: Dp? = null,
    alignEnd: Boolean = false,
) {
    CellText(
        text = text,
        width = width,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        alignEnd = alignEnd,
    )
}

@Composable
private fun CellText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    alignEnd: Boolean = false,
) {
    val widthModifier = if (width != null) Modifier.width(width) else Modifier

    Box(
        modifier = modifier.then(widthModifier),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                shape = PerfViewTokens.cardShape,
            )
            .border(
                width = PerfViewTokens.borderWidth,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                shape = PerfViewTokens.cardShape,
            )
            .padding(PerfViewTokens.cardPadding)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onError,
        )
    }
}
