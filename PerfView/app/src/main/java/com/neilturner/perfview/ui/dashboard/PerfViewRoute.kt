package com.neilturner.perfview.ui.dashboard

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neilturner.perfview.overlay.CpuOverlayService
import com.neilturner.perfview.overlay.OverlayPermissionManager
import com.neilturner.perfview.ui.dashboard.contract.PerfViewCommand
import com.neilturner.perfview.ui.dashboard.contract.PerfViewIntent
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun PerfViewRoute(
    viewModel: PerfViewViewModel = koinViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val overlayPermissionManager = remember { OverlayPermissionManager(context) }

    fun startOverlayAndFinish() {
        ContextCompat.startForegroundService(
            context,
            CpuOverlayService.createStartIntent(context),
        )
        (context as? Activity)?.finish()
    }

    // The notification permission is asked for on the intro screen, before anything finishes.
    // Raising it here used to land a system dialog on top of an Activity that was about to be
    // finished, which could take the process down and lose the ADB session with it.
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.accept(PerfViewIntent.OverlayPermissionResult)
    }
    // ON_START is the single source of truth for loading. A separate LaunchedEffect
    // would fire alongside it on first composition and start a second, racing
    // connection attempt.
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    viewModel.accept(PerfViewIntent.Load)
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.commands.collectLatest { command ->
            when (command) {
                is PerfViewCommand.OpenOverlayPermissionSettings -> {
                    overlayPermissionLauncher.launch(overlayPermissionManager.createPermissionIntent())
                }

                PerfViewCommand.StartBackgroundOverlay -> startOverlayAndFinish()

                PerfViewCommand.ExitApp -> {
                    (context as? Activity)?.finish()
                }
            }
        }
    }

    PerfViewScreen(
        uiState = uiState.value,
        onRunInBackground = { viewModel.accept(PerfViewIntent.RunInBackgroundClicked) },
        onExitApp = { viewModel.accept(PerfViewIntent.ExitApp) },
    )
}
