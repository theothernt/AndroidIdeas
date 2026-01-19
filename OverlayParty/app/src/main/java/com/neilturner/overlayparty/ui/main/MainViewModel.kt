package com.neilturner.overlayparty.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    private val _topLeftText = MutableStateFlow("Top Left")
    val topLeftText: StateFlow<String> = _topLeftText.asStateFlow()

    private val _topRightText = MutableStateFlow("Top Right")
    val topRightText: StateFlow<String> = _topRightText.asStateFlow()

    private val _bottomLeftText = MutableStateFlow("Bottom Left")
    val bottomLeftText: StateFlow<String> = _bottomLeftText.asStateFlow()

    private val _bottomRightText = MutableStateFlow("Bottom Right")
    val bottomRightText: StateFlow<String> = _bottomRightText.asStateFlow()
}
