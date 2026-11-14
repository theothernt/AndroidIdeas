package com.neilturner.musicfun.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the current media session is doing. Anything that is not playable reads as [Stopped]. */
enum class PlaybackStatus {
	Playing,
	Paused,
	Stopped,
}

/**
 * Process wide holder for the currently playing track and its playback position.
 *
 * [MusicNotificationListenerService] writes to it, the UI observes it.
 */
object MusicMetadataRepository {

	private val _metadata = MutableStateFlow<MusicMetadata?>(null)
	val metadata: StateFlow<MusicMetadata?> = _metadata.asStateFlow()

	private val _positionMs = MutableStateFlow(0L)
	val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

	private val _playbackStatus = MutableStateFlow(PlaybackStatus.Stopped)
	val playbackStatus: StateFlow<PlaybackStatus> = _playbackStatus.asStateFlow()

	fun update(metadata: MusicMetadata?) {
		_metadata.value = metadata
	}

	fun updatePlayback(status: PlaybackStatus, positionMs: Long) {
		_playbackStatus.value = status
		_positionMs.value = positionMs.coerceAtLeast(0)
	}

	fun clear() {
		_metadata.value = null
		_positionMs.value = 0L
		_playbackStatus.value = PlaybackStatus.Stopped
	}
}
