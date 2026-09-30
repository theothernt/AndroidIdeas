package com.neilturner.perfview.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neilturner.perfview.ui.components.TvActionButton
import com.neilturner.perfview.ui.components.TvSecondaryButton
import com.neilturner.perfview.ui.dashboard.contract.DashboardContentState
import com.neilturner.perfview.ui.dashboard.contract.PerfViewViewState
import com.neilturner.perfview.ui.theme.PerfViewTheme
import com.neilturner.perfview.ui.theme.PerfViewTvTheme
import java.util.Locale

private val OverlayBackground = Color(0xF2102A36)
private val OverlayText = Color(0xFFEAF5F7)
private val OverlayShape = RoundedCornerShape(18.dp)
private val PanelBorder = Color(0xFF3A5A6A)

/** Width reserved for the action column, so the buttons size to the pane rather than the screen. */
private val ActionsPaneWidth: Dp = 360.dp
private val PaneGap: Dp = 40.dp
private const val VISIBLE_PROCESS_COUNT = 5

/**
 * Fixed process panel size, in dp.
 *
 * The panel used to wrap its longest row, so the card visibly jumped between a short name and a
 * long one on every poll. A fixed footprint keeps the layout still, which matters when the list
 * refreshes every second in front of someone.
 *
 * Sizing is a compromise for a 960dp wide TV screen: it fills most of the pane left over after
 * the action column, gutter and padding, while leaving room for a long package name such as
 * com.android.vending:instant_app_installer to render without being clipped. Height covers the
 * five rows with headroom so a message that wraps cannot push the card taller.
 */
private val ProcessPanelWidth: Dp = 440.dp
private val ProcessPanelHeight: Dp = 180.dp

@Composable
fun PerfViewScreen(
    uiState: PerfViewViewState,
    onRunInBackground: () -> Unit,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07131F))
            .padding(48.dp),
        horizontalArrangement = Arrangement.spacedBy(PaneGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(ActionsPaneWidth),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvActionButton(
                text = "Run in the background",
                onClick = onRunInBackground,
                width = ActionsPaneWidth,
            )

            TvSecondaryButton(
                text = "Exit app",
                onClick = onExitApp,
                width = ActionsPaneWidth,
            )
        }

        ProcessPanel(
            uiState = uiState,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ProcessPanel(
    uiState: PerfViewViewState,
    modifier: Modifier = Modifier,
) {
    val lines = when (val content = uiState.dashboardState?.content) {
        is DashboardContentState.Data -> content.processes.take(VISIBLE_PROCESS_COUNT).mapIndexed { index, process ->
            "${index + 1}. ${String.format(Locale.US, "%.0f%%", process.cpuPercent)}  " +
                "${String.format(Locale.US, "%.0fMB", process.ramMb)}  ${process.name}"
        }

        is DashboardContentState.Loading -> listOf(content.message)
        is DashboardContentState.Empty -> listOf(content.message)
        is DashboardContentState.Unsupported -> listOf("ADB unavailable", content.message)
        null -> List(VISIBLE_PROCESS_COUNT) { "--" }
    }

    Box(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(ProcessPanelWidth)
                .height(ProcessPanelHeight)
                .background(OverlayBackground, OverlayShape)
                .border(1.dp, PanelBorder, OverlayShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            repeat(VISIBLE_PROCESS_COUNT) { index ->
                Text(
                    text = lines.getOrElse(index) { "--" },
                    color = OverlayText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    // The card no longer tracks the text, so an over-long process name is
                    // ellipsised rather than widening or clipping the panel.
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
