package com.neilturner.fadeloop.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.fadeloop.data.model.Video
import com.neilturner.fadeloop.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    private val _videoList = MutableStateFlow<List<Video>>(emptyList())
    val videoList: StateFlow<List<Video>> = _videoList.asStateFlow()

    init {
        loadVideos()
    }

    private fun loadVideos() {
        viewModelScope.launch {
            _videoList.value = repository.getVideos().shuffled()
        }
    }
}
