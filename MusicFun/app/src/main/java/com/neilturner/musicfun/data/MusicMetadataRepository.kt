package com.neilturner.musicfun.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

	private val _isPlaying = MutableStateFlow(false)
	val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

	fun update(metadata: MusicMetadata?) {
		_metadata.value = metadata
	}

	fun updatePlayback(isPlaying: Boolean, positionMs: Long) {
		_isPlaying.value = isPlaying
		_positionMs.value = positionMs.coerceAtLeast(0)
	}

	fun clear() {
		_metadata.value = null
	}
}