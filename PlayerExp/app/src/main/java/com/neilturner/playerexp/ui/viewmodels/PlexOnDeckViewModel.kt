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
import com.neilturner.playerexp.data.plex.PlexLibraryChanges
import com.neilturner.playerexp.data.plex.PlexLibraryEvent
import com.neilturner.playerexp.data.plex.PlexWebSocketObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LOG_TAG = "PlexOnDeck"

/**
 * Plex pushes a firehose of activity, several frames a second while something is playing, so events
 * are collected for [EVENT_QUIET_PERIOD_MILLIS] before anything is fetched. A refetch costs a
 * request and a parse, and the row looks the same a second later either way.
 */
private const val EVENT_QUIET_PERIOD_MILLIS = 2_000L

/**
 * Socket events redraw the row, but Plex does not announce On Deck membership: starting an episode
 * that was not already in the queue produces progress frames and nothing else, so the only way to
 * notice it joined is to ask. One request a minute is cheap and bounds how long a new or finished
 * item can be missing.
 */
private const val REVALIDATE_INTERVAL_MILLIS = 60_000L

/** How long a burst of play positions for an item we do not hold waits before asking again. */
private const val MEMBERSHIP_HINT_QUIET_MILLIS = 3_000L

@Immutable
sealed interface PlexOnDeckUiState {
    data object NotAuthorised : PlexOnDeckUiState
    data object Loading : PlexOnDeckUiState
    data class Success(val items: List<OnDeckItem>) : PlexOnDeckUiState
    data class Error(val message: String) : PlexOnDeckUiState
}

@OptIn(FlowPreview::class)
class PlexOnDeckViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PlexAccountStore(application.applicationContext)
    private val api = PlexApi(store.clientIdentifier())

    private val _uiState = MutableStateFlow<PlexOnDeckUiState>(PlexOnDeckUiState.Loading)
    val uiState: StateFlow<PlexOnDeckUiState> = _uiState.asStateFlow()

    private var requestedPosterSize: IntSize? = null

    /**
     * Refetches are requested through a conflated channel and run on a single consumer, so a burst
     * of events, or arriving while a fetch is already running, collapses into one extra fetch
     * instead of a queue of them.
     */
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    /** Play positions seen for items the row does not hold, which is how a new item announces itself. */
    private val membershipHints = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        viewModelScope.launch {
            for (request in refreshRequests) {
                performRefresh()
            }
        }
        // Progress frames arrive several a second while something is playing, and each one only
        // redraws a bar on a card that is already on screen, so they are applied as they arrive.
        // They are deliberately not debounced: a steady stream of them would keep resetting the
        // timer below and a real library change would never get its refresh.
        viewModelScope.launch {
            PlexWebSocketObserver.events
                .filterIsInstance<PlexLibraryEvent.ProgressChanged>()
                .collect { event ->
                    if (!applyProgress(event)) {
                        // A position for something the row does not hold is the only hint that it
                        // has just joined, since Plex announces no membership change of its own.
                        membershipHints.tryEmit(Unit)
                    }
                }
        }
        // Those hints are settled like a library change: starting an episode sends a burst of them.
        viewModelScope.launch {
            membershipHints
                .debounce(MEMBERSHIP_HINT_QUIET_MILLIS)
                .collect {
                    Log.d(LOG_TAG, "Progress for an item not in the row, refreshing On Deck")
                    requestRefresh()
                }
        }
        // Library changes are the ones worth settling: a scan or a queue change arrives in bursts.
        viewModelScope.launch {
            PlexWebSocketObserver.events
                .filterIsInstance<PlexLibraryEvent.LibraryChanged>()
                .debounce(EVENT_QUIET_PERIOD_MILLIS)
                .collect { event ->
                    Log.d(LOG_TAG, "Library changed (${event.source}), refreshing On Deck")
                    requestRefresh()
                }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(REVALIDATE_INTERVAL_MILLIS)
                Log.d(LOG_TAG, "Periodic revalidation")
                requestRefresh()
            }
        }
    }

    /**
     * Paints whatever is cached straight away, then refreshes. [posterSize] is the pixel size the
     * cards draw at, which is also the size asked of Plex.
     *
     * Every visit revalidates. Coming back to the screen is the natural moment to ask the server what
     * changed while the user was away, and a cached copy is only worth a minute: the row would
     * otherwise keep showing whatever it had, and a new episode started elsewhere would not appear
     * until the copy happened to age out. The cache decides how fast something appears, not whether
     * the server is asked.
     */
    fun loadOnDeck(posterSize: IntSize) {
        val sizeChanged = requestedPosterSize != posterSize
        requestedPosterSize = posterSize

        if (sizeChanged) {
            _uiState.value = PlexOnDeckCache.read(posterSize)
                ?.let { PlexOnDeckUiState.Success(it) }
                ?: PlexOnDeckCache.readStale(posterSize)
                    ?.let { PlexOnDeckUiState.Success(it) }
                ?: PlexOnDeckUiState.Loading
        }
        requestRefresh()
    }

    private fun requestRefresh() {
        refreshRequests.trySend(Unit)
    }

    private suspend fun performRefresh() {
        val posterSize = requestedPosterSize ?: return
        when (val result = withContext(Dispatchers.IO) { fetchOnDeck(posterSize) }) {
            is LoadResult.Authorised -> {
                PlexOnDeckCache.write(result.items, posterSize)
                // The copy on screen now matches the server, so the socket's stale mark is spent.
                PlexLibraryChanges.clear()
                _uiState.value = PlexOnDeckUiState.Success(result.items)
                Log.d(LOG_TAG, "Refreshed ${result.items.size} On Deck items")
            }

            LoadResult.NotAuthorised -> _uiState.value = PlexOnDeckUiState.NotAuthorised
            is LoadResult.Failed -> {
                // A background refresh that fails must not wipe a row the user is looking at.
                if (_uiState.value is PlexOnDeckUiState.Success) {
                    Log.w(LOG_TAG, "Background refresh failed, keeping the row on screen: ${result.message}")
                } else {
                    _uiState.value = PlexOnDeckUiState.Error(result.message)
                }
            }
        }
    }

    /**
     * Redraws one card's bar from a play position rather than refetching the row, so the bar keeps up
     * with playback in another client. Returns false when the row does not hold that item, which is
     * the caller's cue that the row membership itself may have moved.
     */
    private fun applyProgress(event: PlexLibraryEvent.ProgressChanged): Boolean {
        val current = _uiState.value as? PlexOnDeckUiState.Success ?: return false
        val index = current.items.indexOfFirst { it.ratingKey == event.ratingKey }
        if (index < 0) return false

        val items = current.items.toMutableList()
        val item = items[index]
        val updated = item.copy(
            viewOffset = event.viewOffset,
            duration = event.duration?.takeIf { it > 0L } ?: item.duration
        )
        if (updated == item) return true
        items[index] = updated
        Log.d(LOG_TAG, "Progress for ${event.ratingKey}: ${updated.viewOffset}/${updated.duration}")

        // Plex orders On Deck by most recently viewed, so picking an episode up again moves it to
        // the front of the row. Doing it here means the card the user just started is where Plex
        // would put it, instead of waiting for the next fetch; the periodic revalidation settles any
        // disagreement with the server's own ordering.
        if (index > 0) {
            items.removeAt(index)
            items.add(0, updated)
            Log.d(LOG_TAG, "Moved ${event.ratingKey} to the front of the row")
        }

        _uiState.value = PlexOnDeckUiState.Success(items)
        requestedPosterSize?.let { PlexOnDeckCache.write(items, it) }
        return true
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
            Log.d(LOG_TAG, "Loaded ${items.size} On Deck items")
            LoadResult.Authorised(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Failed to load On Deck: ${e.message}", e)
            LoadResult.Failed(
                getApplication<Application>().getString(R.string.plex_on_deck_error, e.message.orEmpty())
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
        data class Authorised(val items: List<OnDeckItem>) : LoadResult
        data class Failed(val message: String) : LoadResult
    }
}
