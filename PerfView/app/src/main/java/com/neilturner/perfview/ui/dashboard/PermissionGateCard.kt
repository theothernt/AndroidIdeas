package com.neilturner.perfview.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neilturner.perfview.ui.theme.PerfViewTokens

@Composable
fun PermissionGateCard(
    uiState: PerfViewViewState,
    onRequestAdbAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(PerfViewTokens.cardSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.screen == PerfViewScreen.Authorizing) {
                WaitingIndicator()
            }

            Text(
                text = uiState.permissionTitle,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = uiState.permissionMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (uiState.screen == PerfViewScreen.Authorizing) {
                Text(
                    text = uiState.statusMessage,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Button(
                    onClick = onRequestAdbAccess,
                    shape = PerfViewTokens.buttonShape,
                ) {
                    Text(text = uiState.permissionButtonLabel)
                }
            }
        }
    }
}
