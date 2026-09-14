package com.neilturner.overlayparty.domain.overlay

import com.neilturner.overlayparty.ui.overlay.OverlayPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OverlayVisibilityManager {
    private val _visibleOverlays =
        MutableStateFlow(
            setOf(
                OverlayPosition.TOP_START,
                OverlayPosition.TOP_END,
                OverlayPosition.BOTTOM_START,
                OverlayPosition.BOTTOM_END,
            ),
        )

    val visibleOverlays: StateFlow<Set<OverlayPosition>> = _visibleOverlays.asStateFlow()

    fun setOverlayVisibility(
        position: OverlayPosition,
        isVisible: Boolean,
    ) {
        _visibleOverlays.update { current ->
            if (isVisible) current + position else current - position
        }
    }

    fun toggleOverlay(position: OverlayPosition) {
        _visibleOverlays.update { current ->
            if (current.contains(position)) current - position else current + position
        }
    }
}