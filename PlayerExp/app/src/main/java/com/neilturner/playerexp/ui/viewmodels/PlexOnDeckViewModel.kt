package com.neilturner.playerexp.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.playerexp.R
import com.neilturner.playerexp.data.plex.OnDeckItem
import com.neilturner.playerexp.data.plex.PlexAccountStore
import com.neilturner.playerexp.data.plex.PlexApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
sealed interface PlexOnDeckUiState {
    data object NotAuthorised : PlexOnDeckUiState
    data object Loading : PlexOnDeckUiState
    data class Success(val items: List<OnDeckItem>) : PlexOnDeckUiState
    data class Error(val message: String) : PlexOnDeckUiState
}

class PlexOnDeckViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PlexAccountStore(application.applicationContext)
    private val api = PlexApi(store.clientIdentifier())

    private val _uiState = MutableStateFlow<PlexOnDeckUiState>(PlexOnDeckUiState.Loading)
    val uiState: StateFlow<PlexOnDeckUiState> = _uiState.asStateFlow()

    init {
        fetchOnDeck()
    }

    fun fetchOnDeck() {
        val token = store.accountToken()
        if (token == null) {
            _uiState.value = PlexOnDeckUiState.NotAuthorised
            return
        }

        _uiState.value = PlexOnDeckUiState.Loading

        viewModelScope.launch {
            try {
                var serverUrl = store.serverUrl()
                if (serverUrl == null) {
                    val info = api.serverInfo(token)
                    serverUrl = info?.uri
                    if (serverUrl != null) {
                        store.saveLinkedAccount(token, info.name, serverUrl)
                    }
                }
                if (serverUrl == null) {
                    _uiState.value = PlexOnDeckUiState.Error(
                        getApplication<Application>().getString(R.string.plex_on_deck_no_server)
                    )
                    return@launch
                }

                val items = api.onDeck(serverUrl, token)
                Log.d("PlexOnDeck", "Loaded ${items.size} On Deck items")
                _uiState.value = PlexOnDeckUiState.Success(items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PlexOnDeck", "Failed to load On Deck: ${e.message}", e)
                _uiState.value = PlexOnDeckUiState.Error(
                    getApplication<Application>().getString(R.string.plex_on_deck_error, e.message.orEmpty())
                )
            }
        }
    }

    override fun onCleared() {
        api.close()
        super.onCleared()
    }
}
