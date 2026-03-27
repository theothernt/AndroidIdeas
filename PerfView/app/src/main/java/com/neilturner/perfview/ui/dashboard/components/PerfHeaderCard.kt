package com.neilturner.perfview.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neilturner.perfview.ui.dashboard.contract.DashboardUiState
import com.neilturner.perfview.ui.theme.PerfViewTokens

@Composable
fun PerfHeaderCard(
    dashboardState: DashboardUiState,
    modifier: Modifier = Modifier,
) {
    DashboardCard(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(PerfViewTokens.cardSpacing),
        ) {
            Text(
                text = "Perf View",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (dashboardState.lastUpdatedLabel == null) {
                        "Waiting for first update"
                    } else {
                        "Last updated ${dashboardState.lastUpdatedLabel}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PollingIndicator(modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun DashboardCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                shape = PerfViewTokens.cardShape,
            )
            .border(
                width = PerfViewTokens.borderWidth,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = PerfViewTokens.cardShape,
            )
            .padding(PerfViewTokens.cardPadding)
    ) {
        content()
    }
}
