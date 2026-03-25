package com.neilturner.perfview.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neilturner.perfview.ui.theme.PerfViewTokens

@Composable
fun PerfHeaderCard(
    dashboardState: DashboardUiState,
    modifier: Modifier = Modifier,
) {
    DashboardCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PerfViewTokens.cardSpacing),
        ) {
            Text(
                text = "Top CPU Processes",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (dashboardState.lastUpdatedLabel == null) {
                        "${dashboardState.sourceLabel} • ${dashboardState.statusLabel}"
                    } else {
                        "${dashboardState.sourceLabel} • Last updated ${dashboardState.lastUpdatedLabel}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dashboardState.content is DashboardContentState.Loading) {
                    PollingIndicator()
                }
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
