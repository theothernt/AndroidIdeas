package com.neilturner.perfview.ui.intro

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neilturner.perfview.platform.OverlayAccessChecker
import com.neilturner.perfview.ui.intro.contract.IntroCommand
import com.neilturner.perfview.ui.intro.contract.IntroIntent
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun IntroRoute(
    onNavigateToDashboard: () -> Unit,
    viewModel: IntroViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val overlayAccessChecker: OverlayAccessChecker = koinInject()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.accept(IntroIntent.NotificationPermissionResult(granted = granted))
    }

    // There is no dialog for the overlay permission, so granting it means a trip to Settings.
    // Returning here is what re-checks it, since the grant is decided on that screen.
    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.accept(IntroIntent.OverlaySettingsResult)
    }

    // Subscribed before the first check runs. LaunchedEffect and DisposableEffect are otherwise
    // ordered arbitrarily, and a check that emits the permission request before anything is
    // collecting would drop it silently, leaving the checklist spinning forever.
    LaunchedEffect(viewModel) {
        viewModel.commands.collectLatest { command ->
            when (command) {
                IntroCommand.NavigateToDashboard -> onNavigateToDashboard()

                IntroCommand.RequestNotificationPermission -> notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS,
                )

                IntroCommand.OpenOverlaySettings -> overlaySettingsLauncher.launch(
                    overlayAccessChecker.createGrantIntent(),
                )

                IntroCommand.ExitApp -> (context as? Activity)?.finish()
            }
        }
    }

    // ON_START rather than a plain LaunchedEffect, so a return from the permission dialog or
    // Settings re-checks what the user may have just changed there, instead of racing a second
    // pass against the in-flight one.
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.accept(IntroIntent.Load)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    IntroScreen(
        uiState = uiState,
        onAction = { viewModel.accept(IntroIntent.ActionClicked) },
        onExitApp = { viewModel.accept(IntroIntent.ExitApp) },
    )
}