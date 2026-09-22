package com.neilturner.playerexp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.playerexp.R
import com.neilturner.playerexp.ui.viewmodels.PlexSettingsUiState
import com.neilturner.playerexp.ui.viewmodels.PlexSettingsViewModel
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlexSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlexSettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onScreenVisible()
                Lifecycle.Event.ON_STOP -> viewModel.onScreenHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenHidden()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 96.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(Modifier.height(40.dp))
        if (state.isLinked) {
            ConnectedContent(state, viewModel::disconnect)
        } else {
            LinkingContent(state)
        }
        Spacer(Modifier.height(44.dp))
        TvActionButton(text = stringResource(R.string.back), onClick = onBack)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LinkingContent(state: PlexSettingsUiState) {
    Text(
        text = stringResource(R.string.plex_link_instruction),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(32.dp))
    Text(
        text = state.linkingCode.orEmpty().uppercase(Locale.ROOT),
        style = MaterialTheme.typography.displayLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(28.dp))
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text(
        text = when {
            state.isRetrying -> stringResource(R.string.plex_retrying)
            state.codeWasRefreshed -> stringResource(R.string.plex_code_refreshed)
            else -> stringResource(R.string.plex_waiting)
        },
        style = MaterialTheme.typography.titleLarge
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ConnectedContent(state: PlexSettingsUiState, onDisconnect: () -> Unit) {
    val disconnectFocus = androidx.compose.runtime.remember { FocusRequester() }
    LaunchedEffect(Unit) {
        disconnectFocus.requestFocus()
    }
    Text(
        text = stringResource(R.string.plex_connected),
        style = MaterialTheme.typography.headlineLarge
    )
    state.serverName?.let { serverName ->
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.plex_connected_to, serverName),
            style = MaterialTheme.typography.headlineSmall
        )
    }
    Spacer(Modifier.height(40.dp))
    TvActionButton(
        text = stringResource(R.string.plex_disconnect),
        onClick = onDisconnect,
        modifier = Modifier.focusRequester(disconnectFocus)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.width(360.dp).height(56.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
