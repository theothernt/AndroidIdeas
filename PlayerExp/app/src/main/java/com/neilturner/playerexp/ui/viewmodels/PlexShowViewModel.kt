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
import com.neilturner.playerexp.data.plex.PlexShow
import com.neilturner.playerexp.data.plex.groupEpisodesBySeason
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

private const val LOG_TAG = "PlexShow"
private const val EVENT_QUIET_PERIOD_MILLIS = 2_000L

@Immutable
sealed interface PlexShowUiState {
    data object NotAuthorised : PlexShowUiState
    data object Loading : PlexShowUiState
    data class Success(val show: PlexShow) : PlexShowUiState
    data class Error(val message: String) : PlexShowUiState
}

/**
 * The pixel sizes the show screen draws at, handed in from the screen because only the layout
 * knows them. Plex serves artwork at whatever size it was scanned at, so every image on this
 * screen is asked for at the size it is laid out rather than downloaded whole and scaled down.
 */
@Immutable
data class PlexShowImageSizes(
    val poster: IntSize,
    val art: IntSize,
    val still: IntSize,
    /** The box the show's title art is fetched at, so the hero asks for the size it draws. */
    val title: IntSize
)

/**
 * The show detail screen: one visit asks for the show's summary and every episode in one go, and
 * the result is kept in the same conflated-request shape On Deck and the libraries use so a burst of
 * socket events, or arriving while a fetch is already running, collapses into one extra fetch.
 *
 * Unlike On Deck this is a catalogue page, not a live row, so it does not refresh on a timer: a
 * library scan adding or removing an episode is the only thing worth redrawing for, and that
 * arrives as a socket event already.
 */
@OptIn(FlowPreview::class)
class PlexShowViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PlexAccountStore(application.applicationContext)
    private val api = PlexApi(store.clientIdentifier())

    private val _uiState = MutableStateFlow<PlexShowUiState>(PlexShowUiState.Loading)
    val uiState: StateFlow<PlexShowUiState> = _uiState.asStateFlow()

    private var showRatingKey: String? = null
    private var requestedSizes: PlexShowImageSizes? = null
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            for (request in refreshRequests) {
                performRefresh()
            }
        }
        viewModelScope.launch {
            PlexWebSocketObserver.events
                .filterIsInstance<PlexLibraryEvent.LibraryChanged>()
                .debounce(EVENT_QUIET_PERIOD_MILLIS)
                .collect { event ->
                    Log.d(LOG_TAG, "Library changed (${event.source}), refreshing show")
                    requestRefresh()
                }
        }
    }

    /**
     * Paints whatever the last load held straight away is not possible here — the ViewModel is tied
     * to the route and rebuilt per visit — so this is simply "ask the server now", keyed on the
     * show so moving between shows restarts on the right one.
     */
    fun loadShow(showRatingKey: String, sizes: PlexShowImageSizes) {
        this.showRatingKey = showRatingKey
        this.requestedSizes = sizes
        requestRefresh()
    }

    private fun requestRefresh() {
        refreshRequests.trySend(Unit)
    }

    private suspend fun performRefresh() {
        val key = showRatingKey ?: return
        val sizes = requestedSizes ?: return
        when (val result = withContext(Dispatchers.IO) { fetchShow(key, sizes) }) {
            is LoadResult.Authorised -> {
                PlexLibraryChanges.clear()
                _uiState.value = PlexShowUiState.Success(result.show)
                Log.d(
                    LOG_TAG,
                    "Loaded show: ${result.show.title}, seasons=${result.show.seasonCount}, " +
                        "episodes=${result.show.episodeCount}, rows=${result.show.seasons.size}, " +
                        "titleArt=${result.show.clearLogoUrl != null}"
                )
            }

            LoadResult.NotAuthorised -> _uiState.value = PlexShowUiState.NotAuthorised
            is LoadResult.Failed -> {
                if (_uiState.value is PlexShowUiState.Success) {
                    Log.w(LOG_TAG, "Background refresh failed, keeping the show on screen: ${result.message}")
                } else {
                    _uiState.value = PlexShowUiState.Error(result.message)
                }
            }
        }
    }

    /** Runs on [Dispatchers.IO]: the Ktor parses, URL building and logging all happen here. */
    private suspend fun fetchShow(showRatingKey: String, sizes: PlexShowImageSizes): LoadResult {
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

            coroutineScope {
                // The summary and the episode catalog do not depend on each other.
                val details = async {
                    api.showDetails(
                        serverUrl = serverUrl,
                        accountToken = token,
                        showRatingKey = showRatingKey,
                        posterWidthPx = sizes.poster.width,
                        posterHeightPx = sizes.poster.height,
                        artWidthPx = sizes.art.width,
                        artHeightPx = sizes.art.height,
                        logoWidthPx = sizes.title.width,
                        logoHeightPx = sizes.title.height
                    )
                }
                val episodes = async {
                    api.showEpisodes(
                        serverUrl = serverUrl,
                        accountToken = token,
                        showRatingKey = showRatingKey,
                        stillWidthPx = sizes.still.width,
                        stillHeightPx = sizes.still.height
                    )
                }
                // The metadata carries the logo only on servers that put it there; most keep it on
                // the clearLogos listing instead, and that listing is only worth asking for when the
                // metadata had none. It is started alongside the other two rather than after them so
                // waiting for it never costs an extra round-trip on the way to painting the screen.
                val titleArt = async {
                    api.showTitleArt(
                        serverUrl = serverUrl,
                        accountToken = token,
                        showRatingKey = showRatingKey,
                        widthPx = sizes.title.width,
                        heightPx = sizes.title.height
                    )
                }

                val show = details.await()
                val grouped = groupEpisodesBySeason(episodes.await())
                val logoUrl = show.clearLogoUrl ?: titleArt.await()
                LoadResult.Authorised(
                    show.copy(
                        seasons = grouped,
                        episodeCount = grouped.sumOf { it.episodes.size },
                        watchedEpisodeCount = grouped.sumOf { season -> season.episodes.count { it.isWatched } },
                        clearLogoUrl = logoUrl
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Failed to load show: ${e.message}", e)
            LoadResult.Failed(
                getApplication<Application>().getString(R.string.plex_show_error, e.message.orEmpty())
            )
        }
    }

    override fun onCleared() {
        refreshRequests.close()
        api.close()
        super.onCleared()
    }

    private sealed interface LoadResult {
        data object NotAuthorised : LoadResult
        data class Authorised(val show: PlexShow) : LoadResult
        data class Failed(val message: String) : LoadResult
    }
}