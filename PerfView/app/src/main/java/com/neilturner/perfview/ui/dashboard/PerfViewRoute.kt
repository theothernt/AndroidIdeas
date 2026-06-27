package com.neilturner.perfview.ui.dashboard

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neilturner.perfview.overlay.CpuOverlayService
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
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.accept(PerfViewIntent.OverlayPermissionResult)
    }

    LaunchedEffect(viewModel) {
        viewModel.accept(PerfViewIntent.Load)
    }

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
                is PerfViewCommand.OpenOverlayPermissionSettings ->
                    overlayPermissionLauncher.launch(command.intent)

                PerfViewCommand.StartBackgroundOverlay -> {
                    ContextCompat.startForegroundService(
                        context,
                        CpuOverlayService.createStartIntent(context),
                    )
                    (context as? Activity)?.finish()
                }

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
