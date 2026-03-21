package com.neilturner.perfview.ui.dashboard

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.perfview.data.cpu.TopProcessUsage
import com.neilturner.perfview.overlay.CpuOverlayService
import com.neilturner.perfview.ui.theme.PerfViewTheme
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun PerfViewRoute(
    viewModel: PerfViewViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.accept(PerfViewIntent.OverlayPermissionResult)
    }

    LaunchedEffect(Unit) {
        viewModel.accept(PerfViewIntent.Load)
    }

    LaunchedEffect(viewModel) {
        viewModel.commands.collectLatest { command ->
            when (command) {
                is PerfViewCommand.OpenOverlayPermissionSettings ->
                    overlayPermissionLauncher.launch(command.intent)

                PerfViewCommand.StartBackgroundOverlay -> {
                    ContextCompat.startForegroundService(
                        context,
                        CpuOverlayService.createStartIntent(context),
                    )
                    (context as? Activity)?.finish()
                }
            }
        }
    }

    PerfViewScreen(
        uiState = uiState,
        onRequestAdbAccess = { viewModel.accept(PerfViewIntent.RequestAdbAccess) },
        onRunInBackground = { viewModel.accept(PerfViewIntent.RunInBackgroundClicked) },
    )
}

@Composable
fun PerfViewScreen(
    uiState: PerfViewViewState,
    onRequestAdbAccess: () -> Unit = {},
    onRunInBackground: () -> Unit = {},
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
        when (uiState.screen) {
            PerfViewScreen.PermissionRationale,
            PerfViewScreen.Authorizing,
            PerfViewScreen.AuthorizationFailed -> PermissionGate(
                uiState = uiState,
                onRequestAdbAccess = onRequestAdbAccess,
            )

            PerfViewScreen.Content -> {
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
                BackgroundAction(
                    message = uiState.backgroundActionMessage,
                    onRunInBackground = onRunInBackground,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}

@Composable
private fun BackgroundAction(
    message: String?,
    onRunInBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }
    val frameBorderColor = when {
        isPressed -> Color(0xFFF7F7FA)
        isFocused -> Color(0xCCFFFFFF)
        else -> Color(0x00000000)
    }
    val buttonScale = when {
        isPressed -> 0.985f
        isFocused -> 1.03f
        else -> 1f
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFD2E6E9),
                textAlign = TextAlign.End,
            )
        }
        Button(
            onClick = onRunInBackground,
            interactionSource = interactionSource,
            modifier = Modifier
                .onFocusChanged { isFocused = it.isFocused }
                .scale(buttonScale)
                .border(
                    width = if (isFocused || isPressed) 2.dp else 0.dp,
                    color = frameBorderColor,
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(2.dp),
            colors = ButtonDefaults.colors(
                containerColor = Color(0xFFF2F2F4),
                focusedContainerColor = Color(0xFFFFFFFF),
                pressedContainerColor = Color(0xFFE7E7EC),
                contentColor = Color(0xFF171A20),
                focusedContentColor = Color(0xFF171A20),
                pressedContentColor = Color(0xFF171A20),
            ),
            shape = ButtonDefaults.shape(RoundedCornerShape(16.dp)),
        ) {
            Text(text = "Run in the background")
        }
    }
}

@Composable
private fun PermissionGate(
    uiState: PerfViewViewState,
    onRequestAdbAccess: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0x33224452),
                    shape = RoundedCornerShape(28.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0x30D2E6E9),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 40.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.screen == PerfViewScreen.Authorizing) {
                WaitingIndicator()
            }

            Text(
                text = uiState.permissionTitle,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = uiState.permissionMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFD2E6E9),
                textAlign = TextAlign.Center,
            )
            if (uiState.screen == PerfViewScreen.Authorizing) {
                Text(
                    text = uiState.statusMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF8BE8FF),
                    textAlign = TextAlign.Center,
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = onRequestAdbAccess) {
                    Text(text = uiState.permissionButtonLabel)
                }
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
private fun WaitingIndicator() {
    val transition = rememberInfiniteTransition(label = "authorization")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "authorizationRotation",
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .rotate(rotation),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(72.dp)) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF52E3B0),
                        Color(0xFF8BE8FF),
                        Color(0xFF52E3B0),
                    )
                ),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx()),
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
        HeaderCell(text = "Process", width = 620.dp)
        HeaderCell(text = "CPU", width = 72.dp, alignEnd = true)
    }
}

@Composable
private fun ProcessRow(
    rank: Int,
    name: String,
    cpuPercent: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CellText(text = rank.toString(), width = 34.dp, color = Color(0xFF8BE8FF))
        CellText(text = name, width = 620.dp, color = Color.White)
        CellText(text = String.format("%.1f%%", cpuPercent), width = 72.dp, color = Color(0xFF52E3B0), alignEnd = true)
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
                text = "Restore ADB access to resume live process updates.",
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
                screen = PerfViewScreen.Content,
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
