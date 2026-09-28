package com.neilturner.perfview.ui.dashboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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

    fun startOverlayAndFinish() {
        ContextCompat.startForegroundService(
            context,
            CpuOverlayService.createStartIntent(context),
        )
        (context as? Activity)?.finish()
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.accept(PerfViewIntent.OverlayPermissionResult)
    }

    // The foreground service notification is the only affordance for stopping the
    // overlay, so ask for POST_NOTIFICATIONS before handing off. A denial is not
    // fatal: the overlay still runs, it just becomes harder to dismiss.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        startOverlayAndFinish()
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
                is PerfViewCommand.OpenOverlayPermissionSettings ->
                    overlayPermissionLauncher.launch(command.intent)

                PerfViewCommand.StartBackgroundOverlay -> {
                    if (needsNotificationPermission(context)) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startOverlayAndFinish()
                    }
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

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
