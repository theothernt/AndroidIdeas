package com.neilturner.musicfun.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.musicfun.data.MusicMetadata
import com.neilturner.musicfun.data.MusicMetadataRepository
import com.neilturner.musicfun.data.lyrics.LrcLine
import com.neilturner.musicfun.data.lyrics.LyricsRepository
import com.neilturner.musicfun.data.lyrics.LyricsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LyricsUiState {
	data object Loading : LyricsUiState
	data object None : LyricsUiState
	data object Instrumental : LyricsUiState
	data class Synced(val lines: List<LrcLine>) : LyricsUiState
	data class Plain(val text: String) : LyricsUiState
}

class MusicViewModel : ViewModel() {

	val metadata: StateFlow<MusicMetadata?> = MusicMetadataRepository.metadata
	val positionMs: StateFlow<Long> = MusicMetadataRepository.positionMs

	private val _lyrics = MutableStateFlow<LyricsUiState>(LyricsUiState.Loading)
	val lyrics: StateFlow<LyricsUiState> = _lyrics.asStateFlow()

	init {
		viewModelScope.launch {
			var lastTrackKey: String? = null
			MusicMetadataRepository.metadata.collect { metadata ->
				// Progress updates produce new metadata objects but the same track.
				val trackKey = metadata?.let { "${it.artist}|${it.title}|${it.album}" }
				if (trackKey == lastTrackKey) return@collect
				lastTrackKey = trackKey

				if (metadata == null) {
					_lyrics.value = LyricsUiState.None
					return@collect
				}

				_lyrics.value = LyricsUiState.Loading
				_lyrics.value = when (val result = LyricsRepository.fetch(metadata)) {
					is LyricsResult.Synced -> LyricsUiState.Synced(result.lines)
					is LyricsResult.Plain -> LyricsUiState.Plain(result.text)
					LyricsResult.Instrumental -> LyricsUiState.Instrumental
					LyricsResult.None -> LyricsUiState.None
				}
			}
		}
	}
}