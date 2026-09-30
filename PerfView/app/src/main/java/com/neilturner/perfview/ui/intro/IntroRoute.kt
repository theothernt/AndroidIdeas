package com.neilturner.perfview.ui.intro

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neilturner.perfview.ui.intro.contract.IntroCommand
import com.neilturner.perfview.ui.intro.contract.IntroIntent
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun IntroRoute(
    onNavigateToDashboard: () -> Unit,
    viewModel: IntroViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ON_START rather than a LaunchedEffect, so a return from the system debugging dialog
    // does not start a second, racing probe against the in-flight one.
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.accept(IntroIntent.Load)
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
                IntroCommand.NavigateToDashboard -> onNavigateToDashboard()
                IntroCommand.ExitApp -> (context as? Activity)?.finish()
            }
        }
    }

    IntroScreen(
        uiState = uiState,
        onRetry = { viewModel.accept(IntroIntent.RetryClicked) },
        onExitApp = { viewModel.accept(IntroIntent.ExitApp) },
    )
}