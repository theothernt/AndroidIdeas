package com.neilturner.musicfun.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process wide holder for the currently playing track.
 *
 * [MusicNotificationListenerService] writes to it, the UI observes it.
 */
object MusicMetadataRepository {

	private val _metadata = MutableStateFlow<MusicMetadata?>(null)

	val metadata: StateFlow<MusicMetadata?> = _metadata.asStateFlow()

	fun update(metadata: MusicMetadata?) {
		_metadata.value = metadata
	}

	fun clear() {
		_metadata.value = null
	}
}