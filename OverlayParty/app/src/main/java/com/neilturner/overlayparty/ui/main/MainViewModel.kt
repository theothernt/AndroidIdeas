package com.neilturner.overlayparty.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.overlayparty.data.LocationRepository
import com.neilturner.overlayparty.data.MusicRepository
import com.neilturner.overlayparty.data.TimeRepository
import com.neilturner.overlayparty.data.WeatherRepository
import com.neilturner.overlayparty.ui.overlay.OverlayContent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    weatherRepository: WeatherRepository,
    timeRepository: TimeRepository,
    musicRepository: MusicRepository,
    locationRepository: LocationRepository
) : ViewModel() {

    val topStartOverlay: StateFlow<OverlayContent?> = weatherRepository.getWeatherStream()
        .map { OverlayContent.TextOnly(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OverlayContent.TextOnly("Loading Weather...")
        )

    val topEndOverlay: StateFlow<OverlayContent?> = timeRepository.getTimeStream()
        .map { OverlayContent.TextOnly(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OverlayContent.TextOnly("Loading Time...")
        )

    val bottomStartOverlay: StateFlow<OverlayContent?> = musicRepository.getMusicStream()
        .map { OverlayContent.IconWithText(it, Icons.Default.MusicNote) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OverlayContent.IconWithText("Loading Music...", Icons.Default.MusicNote)
        )

    val bottomEndOverlay: StateFlow<OverlayContent?> = locationRepository.getLocationStream()
        .map { OverlayContent.TextOnly(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OverlayContent.TextOnly("Loading Location...")
        )
}
