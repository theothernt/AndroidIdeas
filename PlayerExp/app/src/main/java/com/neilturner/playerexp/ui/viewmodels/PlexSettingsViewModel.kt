package com.neilturner.playerexp.ui.viewmodels

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.playerexp.data.plex.PlexAccountStore
import com.neilturner.playerexp.data.plex.PlexApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class PlexSettingsUiState(
    val isLinked: Boolean = false,
    val linkingCode: String? = null,
    val serverName: String? = null,
    val isRetrying: Boolean = false,
    val codeWasRefreshed: Boolean = false
)

class PlexSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PlexAccountStore(application.applicationContext)
    private val api = PlexApi(store.clientIdentifier())
    private val _state = MutableStateFlow(
        PlexSettingsUiState(
            isLinked = store.accountToken() != null,
            serverName = store.serverName()
        )
    )
    val state: StateFlow<PlexSettingsUiState> = _state.asStateFlow()
    private var pollingJob: Job? = null

    init {
        if (!state.value.isLinked) startLinking()
    }

    fun onScreenVisible() {
        if (!state.value.isLinked) startLinking()
    }

    fun onScreenHidden() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun disconnect() {
        onScreenHidden()
        store.clearPlexData()
        _state.update { PlexSettingsUiState() }
        startLinking()
    }

    private fun startLinking() {
        if (pollingJob?.isActive == true || state.value.isLinked) return
        pollingJob = viewModelScope.launch {
            var retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
            while (isActive && !state.value.isLinked) {
                try {
                    val createdPin = api.createPin()
                    _state.update {
                        it.copy(
                            linkingCode = createdPin.code,
                            isRetrying = false,
                            codeWasRefreshed = it.linkingCode != null
                        )
                    }
                    val pinCreatedAt = System.currentTimeMillis()
                    retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
                    while (isActive && System.currentTimeMillis() - pinCreatedAt < PIN_LIFETIME_MILLIS) {
                        delay(POLL_INTERVAL_MILLIS)
                        try {
                            val pin = api.pin(createdPin.id)
                            val token = pin.authToken
                            if (token != null) {
                                val serverInfo = runCatching { api.serverInfo(token) }.getOrNull()
                                store.saveLinkedAccount(token, serverInfo?.name, serverInfo?.uri)
                                _state.update {
                                    PlexSettingsUiState(isLinked = true, serverName = serverInfo?.name)
                                }
                                return@launch
                            }
                            _state.update { it.copy(isRetrying = false) }
                            retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
                        } catch (_: Exception) {
                            _state.update { it.copy(isRetrying = true) }
                            delay(retryDelayMillis)
                            retryDelayMillis = (retryDelayMillis * 2).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
                        }
                    }
                } catch (_: Exception) {
                    _state.update { it.copy(isRetrying = true) }
                    delay(retryDelayMillis)
                    retryDelayMillis = (retryDelayMillis * 2).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
                }
            }
        }
    }

    override fun onCleared() {
        onScreenHidden()
        api.close()
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 2_500L
        const val PIN_LIFETIME_MILLIS = 14 * 60 * 1_000L
        const val INITIAL_RETRY_DELAY_MILLIS = 2_000L
        const val MAX_RETRY_DELAY_MILLIS = 30_000L
    }
}
