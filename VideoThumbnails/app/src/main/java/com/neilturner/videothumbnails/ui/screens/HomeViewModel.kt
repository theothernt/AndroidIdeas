package com.neilturner.videothumbnails.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.videothumbnails.data.Video
import com.neilturner.videothumbnails.data.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VideoUiState {
    data object Loading : VideoUiState
    data class Success(val videos: List<Video>) : VideoUiState
    data class Error(val message: String) : VideoUiState
}

class HomeViewModel(private val repository: VideoRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<VideoUiState>(VideoUiState.Loading)
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    init {
        loadVideos()
    }

    private fun loadVideos() {
        viewModelScope.launch {
            try {
                val videos = repository.getVideos()
                _uiState.value = VideoUiState.Success(videos)
            } catch (e: Exception) {
                _uiState.value = VideoUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
