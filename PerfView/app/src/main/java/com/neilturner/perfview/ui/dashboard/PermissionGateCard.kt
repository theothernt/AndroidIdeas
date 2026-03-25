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
    permissionState: PermissionUiState,
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
            if (permissionState.phase == PermissionPhase.Authorizing) {
                WaitingIndicator()
            }

            Text(
                text = permissionState.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = permissionState.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            val detailMessage = permissionState.detailMessage
            if (permissionState.phase == PermissionPhase.Authorizing && detailMessage != null) {
                Text(
                    text = detailMessage,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                )
            } else {
                if (!detailMessage.isNullOrBlank()) {
                    Text(
                        text = detailMessage,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Button(
                    onClick = onRequestAdbAccess,
                    shape = PerfViewTokens.buttonShape,
                ) {
                    Text(text = permissionState.buttonLabel)
                }
            }
        }
    }
}
