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
import kotlinx.coroutines.flow.first
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

    private val _selectedVideo = MutableStateFlow<Video?>(null)
    val selectedVideo: StateFlow<Video?> = _selectedVideo.asStateFlow()

    val categories: List<VideoCategory> = AerialCategories.all

    private val _selectedCategory = MutableStateFlow(categories.first())
    val selectedCategory: StateFlow<VideoCategory> = _selectedCategory.asStateFlow()

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
            } else {
                state.videos.filter { video -> AerialCategories.forVideo(video) == category }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    val selectedCategoryCounts: StateFlow<SelectionCounts> =
        combine(_uiState, _selectedCategory, selectionStore.hiddenVideoIds) { state, category, hiddenVideoIds ->
            val videos = (state as? VideoUiState.Success)?.videos.orEmpty()
            val inCategory = videos.filter { video -> AerialCategories.forVideo(video) == category }
            val hidden = inCategory.count { video -> video.id in hiddenVideoIds }
            SelectionCounts(
                selected = inCategory.size - hidden,
                total = inCategory.size,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SelectionCounts(),
        )

    val selectionCounts: StateFlow<SelectionCounts> =
        combine(_uiState, selectionStore.hiddenVideoIds) { state, hiddenVideoIds ->
            val videos = (state as? VideoUiState.Success)?.videos.orEmpty()
            val hiddenInLibrary = videos.count { video -> video.id in hiddenVideoIds }
            SelectionCounts(
                selected = videos.size - hiddenInLibrary,
                total = videos.size,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SelectionCounts(),
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

    fun selectCategory(category: VideoCategory) {
        _selectedCategory.value = category
    }

    fun toggleVideoHidden(video: Video) {
        viewModelScope.launch {
            selectionStore.toggle(video.id)
        }
    }

    fun toggleCategoryVisibility() {
        viewModelScope.launch {
            val state = _uiState.value as? VideoUiState.Success ?: return@launch
            val category = _selectedCategory.value
            val categoryVideoIds =
                state.videos
                    .filterTo(mutableSetOf()) { video -> AerialCategories.forVideo(video) == category }
                    .mapTo(mutableSetOf()) { video -> video.id }
            if (categoryVideoIds.isEmpty()) {
                return@launch
            }
            val hiddenVideoIds = selectionStore.hiddenVideoIds.first()
            val allAlreadyHidden = categoryVideoIds.all { videoId -> videoId in hiddenVideoIds }
            selectionStore.setHiddenAll(categoryVideoIds, hidden = !allAlreadyHidden)
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

data class SelectionCounts(
    val selected: Int = 0,
    val total: Int = 0,
)
