package com.neilturner.perfview.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.neilturner.perfview.ui.dashboard.contract.BackgroundActionUiState
import com.neilturner.perfview.ui.theme.PerfViewTokens

@Composable
fun BackgroundActionCard(
    backgroundActionState: BackgroundActionUiState,
    onRunInBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PerfViewTokens.cardSpacing),
    ) {
        if (!backgroundActionState.backgroundActionMessage.isNullOrBlank()) {
            Text(
                text = backgroundActionState.backgroundActionMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Start,
            )
        }
        Button(
            onClick = onRunInBackground,
            shape = PerfViewTokens.buttonShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Run in the background",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
