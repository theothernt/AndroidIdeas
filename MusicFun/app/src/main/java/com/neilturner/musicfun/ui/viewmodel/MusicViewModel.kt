package com.neilturner.musicfun.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.musicfun.data.MusicMetadata
import com.neilturner.musicfun.data.MusicMetadataRepository
import com.neilturner.musicfun.data.PlaybackStatus
import com.neilturner.musicfun.data.facts.ReleaseDate
import com.neilturner.musicfun.data.facts.ReleaseDateRepository
import com.neilturner.musicfun.data.listener.NotificationListenerPermission
import com.neilturner.musicfun.data.lyrics.LrcLine
import com.neilturner.musicfun.data.lyrics.LyricsRepository
import com.neilturner.musicfun.data.lyrics.LyricsResult
import com.neilturner.musicfun.data.trackKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

sealed interface LyricsUiState {
	data object None : LyricsUiState
	data class Synced(val lines: List<LrcLine>) : LyricsUiState
	data class Plain(val text: String) : LyricsUiState
}

/** Lyrics and the track they belong to, so the UI never shows one track's lines with another. */
data class LyricsUi(val trackKey: String, val state: LyricsUiState)

/** "Released 40 years ago · Electronic" and the track it describes; text is null when not found. */
data class ReleaseUi(val trackKey: String, val text: String?)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

	val positionMs: StateFlow<Long> = MusicMetadataRepository.positionMs

	private val _permissionGranted = MutableStateFlow<Boolean?>(null)

	/**
	 * null until the first check finishes, so the UI never flashes a request before it knows.
	 *
	 * A playing status is also proof of access: the listener can only publish one while it is bound.
	 * That covers the moment right after the user grants access in settings, when the system setting
	 * can still read as stale, and stops a late check from hiding access that clearly works.
	 */
	val hasNotificationAccess: StateFlow<Boolean?> = combine(
		_permissionGranted,
		MusicMetadataRepository.playbackStatus,
	) { granted, status ->
		if (status == PlaybackStatus.Playing) true else granted
	}.stateIn(viewModelScope, SharingStarted.Eagerly, null)

	init {
		refreshNotificationAccess()
	}

	/**
	 * Notification-listener access can only be changed in system settings, so re-read it when the
	 * screen resumes. The system call is a binder transaction, so it stays off the main thread.
	 */
	fun refreshNotificationAccess() {
		val context = getApplication<Application>()
		viewModelScope.launch {
			_permissionGranted.value = withContext(Dispatchers.IO) {
				NotificationListenerPermission.isGranted(context)
			}
		}
	}

	/**
	 * The track to show: the session must actually be playing, and the same track must have been
	 * playing for [TrackSettleMs]. Holding skip on the remote changes tracks several times a
	 * second; without the settle window each one would cross-fade and query LRCLIB.
	 */
	@OptIn(FlowPreview::class)
	val nowPlaying: StateFlow<MusicMetadata?> = combine(
		MusicMetadataRepository.metadata
			.map { it?.trackKey }
			.distinctUntilChanged()
			.debounce(TrackSettleMs),
		MusicMetadataRepository.metadata,
		MusicMetadataRepository.playbackStatus,
	) { settledKey, latest, status ->
		if (status == PlaybackStatus.Playing && settledKey != null && latest?.trackKey == settledKey) {
			latest
		} else {
			null
		}
	}
		// Per-tick metadata updates (position, new bitmap instances) must not re-run the fade.
		.distinctUntilChanged { old, new -> old?.trackKey == new?.trackKey }
		.stateIn(viewModelScope, SharingStarted.Eagerly, null)

	private val lastLyrics = MutableStateFlow<LyricsUi?>(null)

	/**
	 * One lookup per settled track. [mapLatest] drops the previous lookup when the track changes
	 * again, and nothing is requested while the session is paused or stopped.
	 */
	@OptIn(ExperimentalCoroutinesApi::class)
	val lyrics: StateFlow<LyricsUi?> = nowPlaying
		.mapLatest { metadata ->
			if (metadata == null) {
				// Keep the last lines so the panel still has content while it fades out.
				lastLyrics.value
			} else {
				LyricsUi(metadata.trackKey, resolveLyrics(metadata)).also { lastLyrics.value = it }
			}
		}
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

	private val lastRelease = MutableStateFlow<ReleaseUi?>(null)

	/**
	 * When the settled track was first released and what genre it is filed under. Same
	 * one-lookup-per-track rule as the lyrics; the previous text stays on screen while the info
	 * fades out.
	 */
	@OptIn(ExperimentalCoroutinesApi::class)
	val release: StateFlow<ReleaseUi?> = nowPlaying
		.mapLatest { metadata ->
			if (metadata == null) {
				lastRelease.value
			} else {
				ReleaseUi(metadata.trackKey, releaseText(metadata)).also { lastRelease.value = it }
			}
		}
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

	/** "Released 40 years ago · Electronic"; either half stands alone when the other is missing. */
	private suspend fun releaseText(metadata: MusicMetadata): String? {
		val facts = ReleaseDateRepository.fetch(metadata) ?: return null
		val released = facts.releaseDate?.let(::relativeTime)?.let { "Released $it" }
		val genre = facts.genre
		return when {
			released != null && genre != null -> "$released · $genre"
			else -> released ?: genre
		}
	}

	/**
	 * "40 years ago", "6 months ago", "12 days ago". Calendar arithmetic rather than java.time,
	 * which would need API 26 or desugaring at this minSdk.
	 */
	private fun relativeTime(date: ReleaseDate): String? {
		val zone = TimeZone.getTimeZone("UTC")
		val released = Calendar.getInstance(zone).apply {
			clear()
			set(date.year, date.month - 1, date.day)
		}
		val now = Calendar.getInstance(zone)
		if (released.after(now)) return null

		var months = (now.get(Calendar.YEAR) - released.get(Calendar.YEAR)) * 12 +
			(now.get(Calendar.MONTH) - released.get(Calendar.MONTH))
		if (now.get(Calendar.DAY_OF_MONTH) < released.get(Calendar.DAY_OF_MONTH)) months -= 1

		return when {
			months >= 24 -> "${months / 12} years ago"
			months >= 12 -> "1 year ago"
			months >= 2 -> "$months months ago"
			months == 1 -> "1 month ago"
			else -> {
				val days = ((now.timeInMillis - released.timeInMillis) / DayMs).toInt()
				when {
					days <= 0 -> "today"
					days == 1 -> "1 day ago"
					else -> "$days days ago"
				}
			}
		}
	}

	private suspend fun resolveLyrics(metadata: MusicMetadata): LyricsUiState =
		when (val result = LyricsRepository.fetch(metadata)) {
			is LyricsResult.Synced -> LyricsUiState.Synced(result.lines)
			is LyricsResult.Plain -> LyricsUiState.Plain(result.text)
			// An instrumental has nothing to show, same as a miss.
			LyricsResult.Instrumental,
			LyricsResult.None -> LyricsUiState.None
		}

	private companion object {
		const val TrackSettleMs = 500L
		const val DayMs = 86_400_000L
	}
}
