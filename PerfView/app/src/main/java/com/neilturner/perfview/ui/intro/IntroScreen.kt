package com.neilturner.perfview.ui.intro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neilturner.perfview.ui.components.TvActionButton
import com.neilturner.perfview.ui.components.TvPanelButtonWidth
import com.neilturner.perfview.ui.components.TvSecondaryButton
import com.neilturner.perfview.ui.intro.contract.ChecklistAction
import com.neilturner.perfview.ui.intro.contract.ChecklistStatus
import com.neilturner.perfview.ui.intro.contract.IntroChecklistItem
import com.neilturner.perfview.ui.intro.contract.IntroViewState
import com.neilturner.perfview.ui.theme.PerfAmber
import com.neilturner.perfview.ui.theme.PerfInk
import com.neilturner.perfview.ui.theme.PerfMist
import com.neilturner.perfview.ui.theme.PerfSlate
import com.neilturner.perfview.ui.theme.PerfViewTheme
import com.neilturner.perfview.ui.theme.PerfViewTvTheme

private val PanelBackground = Color(0xF2102A36)
private val PanelBorder = Color(0xFF3A5A6A)
private val PanelShape = RoundedCornerShape(18.dp)
private val RowDivider = Color(0x1AFFFFFF)

@Composable
fun IntroScreen(
    uiState: IntroViewState,
    onAction: () -> Unit,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PerfInk)
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "PERF VIEW",
                color = PerfMist,
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelBackground, PanelShape)
                    .border(1.dp, PanelBorder, PanelShape)
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            ) {
                uiState.items.forEachIndexed { index, item ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(RowDivider),
                        )
                    }
                    ChecklistRow(item = item)
                }
            }

            uiState.action?.let { action ->
                TvActionButton(
                    text = actionLabel(action),
                    onClick = onAction,
                    width = TvPanelButtonWidth,
                )
            }

            Spacer(modifier = Modifier.size(4.dp))

            TvSecondaryButton(
                text = "Exit app",
                onClick = onExitApp,
                width = TvPanelButtonWidth,
            )
        }
    }
}

private fun actionLabel(action: ChecklistAction): String = when (action) {
    ChecklistAction.RequestNotifications -> "Allow notifications"
    ChecklistAction.OpenOverlaySettings -> "Open Settings"
    ChecklistAction.Retry -> "Try again"
}

@Composable
private fun ChecklistRow(
    item: IntroChecklistItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIndicator(status = item.status)

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                color = PerfMist,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )

            Spacer(modifier = Modifier.size(4.dp))

            Text(
                text = item.detail,
                color = if (item.status == ChecklistStatus.NeedsAttention) PerfAmber else PerfSlate,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun StatusIndicator(
    status: ChecklistStatus,
    modifier: Modifier = Modifier,
) {
    // A fixed box so the spinner and the tick occupy the same space, otherwise the text beside
    // them shifts sideways as each item settles.
    Box(
        modifier = modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            ChecklistStatus.InProgress -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = PerfSlate,
                strokeWidth = 2.dp,
            )

            ChecklistStatus.Ready -> StatusBadge(text = "OK", color = PerfMist)

            ChecklistStatus.NotNeeded -> StatusBadge(text = "N/A", color = PerfSlate)

            ChecklistStatus.NeedsAttention -> StatusBadge(text = "!", color = PerfAmber)
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    color: Color,
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(color.copy(alpha = 0.16f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07131F)
@Composable
private fun IntroScreenPreview() {
    PerfViewTheme(dynamicColor = false) {
        PerfViewTvTheme {
            IntroScreen(
                uiState = IntroViewState(
                    items = listOf(
                        IntroChecklistItem("USB debugging", ChecklistStatus.Ready, "Connected"),
                        IntroChecklistItem("Notification access", ChecklistStatus.Ready, "Granted"),
                        IntroChecklistItem(
                            label = "Overlay access",
                            status = ChecklistStatus.NeedsAttention,
                            detail = "Tap Open Settings to allow",
                        ),
                    ),
                    action = ChecklistAction.OpenOverlaySettings,
                ),
                onAction = {},
                onExitApp = {},
            )
        }
    }
}
