package com.neilturner.channelui.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.channelui.data.Channel
import com.neilturner.channelui.data.ChannelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChannelViewModel(private val channelProvider: ChannelProvider) : ViewModel() {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _currentStreamUrl = MutableStateFlow<String?>(null)
    val currentStreamUrl: StateFlow<String?> = _currentStreamUrl.asStateFlow()

    init {
        loadChannels()
    }

    private fun loadChannels() {
        viewModelScope.launch {
            val channelList = channelProvider.getChannels()
            _channels.value = channelList
            if (channelList.isNotEmpty() && _currentStreamUrl.value == null) {
                // Default to the first channel (NDC World)
                _currentStreamUrl.value = channelList.first().streamUrl
            }
        }
    }

    fun playChannel(channel: Channel) {
        _currentStreamUrl.value = channel.streamUrl
    }
}
