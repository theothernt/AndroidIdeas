package com.neilturner.perfview.ui.intro

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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

                IntroCommand.OpenOverlaySettings -> {
                    // Started directly rather than through an ActivityResultLauncher. This
                    // matches AerialViews: there is no result to read, since Settings reports
                    // success whether or not anything was granted. The re-check on ON_START is
                    // what observes the change.
                    runCatching {
                        context.startActivity(overlayAccessChecker.createGrantIntent())
                    }.onFailure {
                        Log.w(INTRO_TAG, "Could not open the overlay permission screen", it)
                        viewModel.accept(IntroIntent.OverlaySettingsResult)
                    }
                }

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

    IntroScreen(uiState = uiState)
}

private const val INTRO_TAG = "PerfViewIntro"
