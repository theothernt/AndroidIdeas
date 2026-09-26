package com.neilturner.playerexp.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.playerexp.R
import com.neilturner.playerexp.data.plex.OnDeckItem
import com.neilturner.playerexp.data.plex.PlexAccountStore
import com.neilturner.playerexp.data.plex.PlexApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private var requestedPosterSize: IntSize? = null

    /**
     * Paints whatever is cached straight away, then refreshes unless the copy is recent enough to
     * trust. [posterSize] is the pixel size the cards draw at, which is also the size asked of Plex.
     */
    fun loadOnDeck(posterSize: IntSize) {
        if (requestedPosterSize == posterSize) return
        requestedPosterSize = posterSize

        PlexOnDeckCache.read(posterSize)?.let {
            _uiState.value = PlexOnDeckUiState.Success(it)
            return
        }
        _uiState.value = PlexOnDeckCache.readStale(posterSize)
            ?.let { PlexOnDeckUiState.Success(it) }
            ?: PlexOnDeckUiState.Loading

        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { fetchOnDeck(posterSize) }) {
                is LoadResult.Authorised -> {
                    PlexOnDeckCache.write(result.items, posterSize)
                    _uiState.value = PlexOnDeckUiState.Success(result.items)
                }

                LoadResult.NotAuthorised -> _uiState.value = PlexOnDeckUiState.NotAuthorised
                is LoadResult.Failed -> _uiState.value = PlexOnDeckUiState.Error(result.message)
            }
        }
    }

    /** Runs on [Dispatchers.IO]: the Ktor body parse, the URL building and the logging all happen here. */
    private suspend fun fetchOnDeck(posterSize: IntSize): LoadResult {
        val token = store.accountToken() ?: return LoadResult.NotAuthorised
        return try {
            var serverUrl = store.serverUrl()
            if (serverUrl == null) {
                val info = api.serverInfo(token)
                serverUrl = info?.uri
                if (serverUrl != null) {
                    store.saveLinkedAccount(token, info.name, serverUrl)
                }
            }
            if (serverUrl == null) {
                return LoadResult.Failed(
                    getApplication<Application>().getString(R.string.plex_on_deck_no_server)
                )
            }

            val items = api.onDeck(serverUrl, token, posterSize.width, posterSize.height)
            Log.d("PlexOnDeck", "Loaded ${items.size} On Deck items")
            LoadResult.Authorised(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PlexOnDeck", "Failed to load On Deck: ${e.message}", e)
            LoadResult.Failed(
                getApplication<Application>().getString(R.string.plex_on_deck_error, e.message.orEmpty())
            )
        }
    }

    override fun onCleared() {
        api.close()
        super.onCleared()
    }

    private sealed interface LoadResult {
        data object NotAuthorised : LoadResult
        data class Authorised(val items: List<OnDeckItem>) : LoadResult
        data class Failed(val message: String) : LoadResult
    }
}
