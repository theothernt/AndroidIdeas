package com.neilturner.videothumbnails.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.videothumbnails.data.AerialCategories
import com.neilturner.videothumbnails.data.Video
import com.neilturner.videothumbnails.data.VideoCategory
import com.neilturner.videothumbnails.data.VideoRepository
import com.neilturner.videothumbnails.data.VideoSelectionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VideoUiState {
    data object Loading : VideoUiState

    data class Success(
        val videos: List<Video>,
    ) : VideoUiState

    data class Error(
        val message: String,
    ) : VideoUiState
}

class HomeViewModel(
    private val repository: VideoRepository,
    private val selectionStore: VideoSelectionStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow<VideoUiState>(VideoUiState.Loading)
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow<VideoCategory?>(null)
    val selectedCategory: StateFlow<VideoCategory?> = _selectedCategory.asStateFlow()

    private val _selectedVideo = MutableStateFlow<Video?>(null)
    val selectedVideo: StateFlow<Video?> = _selectedVideo.asStateFlow()

    val categories: List<VideoCategory> = AerialCategories.all

    val hiddenVideoIds: StateFlow<Set<String>> =
        selectionStore.hiddenVideoIds
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptySet(),
            )

    val filteredVideos: StateFlow<List<Video>> =
        combine(_uiState, _selectedCategory) { state, category ->
            if (state !is VideoUiState.Success) {
                emptyList()
            } else if (category == null) {
                state.videos
            } else {
                state.videos.filter { video -> AerialCategories.forVideo(video) == category }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

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

    fun selectCategory(category: VideoCategory?) {
        _selectedCategory.value = category
    }

    fun toggleVideoHidden(video: Video) {
        viewModelScope.launch {
            selectionStore.toggle(video.id)
        }
    }

    fun showAllVideos() {
        viewModelScope.launch {
            selectionStore.clearAll()
        }
    }

    fun selectVideo(video: Video) {
        _selectedVideo.value = video
    }

    fun clearSelectedVideo() {
        _selectedVideo.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
