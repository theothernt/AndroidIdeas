package com.neilturner.playerexp.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.playerexp.R
import com.neilturner.playerexp.data.plex.PlexAccountStore
import com.neilturner.playerexp.data.plex.PlexApi
import com.neilturner.playerexp.data.plex.PlexLibraryChanges
import com.neilturner.playerexp.data.plex.PlexLibraryEvent
import com.neilturner.playerexp.data.plex.PlexLibrarySections
import com.neilturner.playerexp.data.plex.PlexLibraryShelf
import com.neilturner.playerexp.data.plex.PlexWebSocketObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LOG_TAG = "PlexLibraries"
private const val LIBRARY_CHANGE_QUIET_PERIOD_MILLIS = 2_000L

@Immutable
sealed interface PlexLibrariesUiState {
    data object NotAuthorised : PlexLibrariesUiState
    data object Loading : PlexLibrariesUiState
    data class Success(val shelves: List<PlexLibraryShelf>) : PlexLibrariesUiState
    data class Error(val message: String) : PlexLibrariesUiState
}

/**
 * The TV Shows and Movies shelves.
 *
 * Which libraries those are is decided by [PlexLibrarySections] rather than by the caller, so a
 * server carrying alternates, music or photos still gets one show row and one movie row. Both are
 * fetched at once because neither depends on the other, and a visit always revalidates, on the same
 * terms as On Deck.
 */
@OptIn(FlowPreview::class)
class PlexLibrariesViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PlexAccountStore(application.applicationContext)
    private val api = PlexApi(store.clientIdentifier())

    private val _uiState = MutableStateFlow<PlexLibrariesUiState>(PlexLibrariesUiState.Loading)
    val uiState: StateFlow<PlexLibrariesUiState> = _uiState.asStateFlow()

    private var requestedPosterSize: IntSize? = null
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            for (request in refreshRequests) {
                performRefresh()
            }
        }
        // A scan finishing or an item being added is the only thing that changes these shelves, and
        // it arrives in bursts, so it is settled before the server is asked.
        viewModelScope.launch {
            PlexWebSocketObserver.events
                .filterIsInstance<PlexLibraryEvent.LibraryChanged>()
                .debounce(LIBRARY_CHANGE_QUIET_PERIOD_MILLIS)
                .collect {
                    Log.d(LOG_TAG, "Library changed (${it.source}), refreshing shelves")
                    requestRefresh()
                }
        }
    }

    fun loadLibraries(posterSize: IntSize) {
        val sizeChanged = requestedPosterSize != posterSize
        requestedPosterSize = posterSize

        if (sizeChanged) {
            _uiState.value = PlexLibrariesCache.read(posterSize)
                ?.let { PlexLibrariesUiState.Success(it) }
                ?: PlexLibrariesCache.readStale(posterSize)
                    ?.let { PlexLibrariesUiState.Success(it) }
                ?: PlexLibrariesUiState.Loading
        }
        requestRefresh()
    }

    private fun requestRefresh() {
        refreshRequests.trySend(Unit)
    }

    private suspend fun performRefresh() {
        val posterSize = requestedPosterSize ?: return
        when (val result = withContext(Dispatchers.IO) { fetchShelves(posterSize) }) {
            is LoadResult.Authorised -> {
                PlexLibrariesCache.write(result.shelves, posterSize)
                PlexLibraryChanges.clear()
                _uiState.value = PlexLibrariesUiState.Success(result.shelves)
                Log.d(
                    LOG_TAG,
                    "Refreshed ${result.shelves.size} shelves: " +
                        result.shelves.joinToString { "${it.sectionTitle}=${it.items.size}" }
                )
            }

            LoadResult.NotAuthorised -> _uiState.value = PlexLibrariesUiState.NotAuthorised
            is LoadResult.Failed -> {
                if (_uiState.value is PlexLibrariesUiState.Success) {
                    Log.w(LOG_TAG, "Background refresh failed, keeping the shelves: ${result.message}")
                } else {
                    _uiState.value = PlexLibrariesUiState.Error(result.message)
                }
            }
        }
    }

    /** Runs on [Dispatchers.IO]: the Ktor parses, URL building and logging all happen there. */
    private suspend fun fetchShelves(posterSize: IntSize): LoadResult {
        val token = store.accountToken() ?: return LoadResult.NotAuthorised
        return try {
            val serverUrl = resolveServerUrl(token) ?: return LoadResult.Failed(
                getApplication<Application>().getString(R.string.plex_on_deck_no_server)
            )

            val sections = api.librarySections(serverUrl, token)
            val picks = listOfNotNull(
                PlexLibrarySections.pickTvShows(sections)?.let { it to tvLabel() },
                PlexLibrarySections.pickMovies(sections)?.let { it to movieLabel() }
            )
            if (picks.isEmpty()) {
                return LoadResult.Failed(
                    getApplication<Application>().getString(R.string.plex_libraries_none_found)
                )
            }

            val shelves = coroutineScope {
                picks.map { (section, label) ->
                    async {
                        PlexLibraryShelf(
                            sectionKey = section.key,
                            sectionTitle = label,
                            items = api.libraryItems(
                                serverUrl = serverUrl,
                                accountToken = token,
                                sectionKey = section.key,
                                posterWidthPx = posterSize.width,
                                posterHeightPx = posterSize.height
                            )
                        )
                    }
                }.map { it.await() }
            }
            LoadResult.Authorised(shelves)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Failed to load libraries: ${e.message}", e)
            LoadResult.Failed(
                getApplication<Application>().getString(R.string.plex_on_deck_error, e.message.orEmpty())
            )
        }
    }

    private suspend fun resolveServerUrl(token: String): String? {
        store.serverUrl()?.let { return it }
        val info = api.serverInfo(token)
        val serverUrl = info?.uri
        if (serverUrl != null) {
            store.saveLinkedAccount(token, info.name, serverUrl)
        }
        return serverUrl
    }

    /** Shown as the row heading: the server's own name for an alternate library reads as clutter. */
    private fun tvLabel(): String = getApplication<Application>().getString(R.string.plex_libraries_tv_shows)

    private fun movieLabel(): String = getApplication<Application>().getString(R.string.plex_libraries_movies)

    override fun onCleared() {
        refreshRequests.close()
        api.close()
        super.onCleared()
    }

    private sealed interface LoadResult {
        data object NotAuthorised : LoadResult
        data class Authorised(val shelves: List<PlexLibraryShelf>) : LoadResult
        data class Failed(val message: String) : LoadResult
    }
}
