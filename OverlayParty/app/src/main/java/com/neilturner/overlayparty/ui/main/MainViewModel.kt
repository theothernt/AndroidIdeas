package com.neilturner.overlayparty.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.overlayparty.data.LocationRepository
import com.neilturner.overlayparty.data.MusicRepository
import com.neilturner.overlayparty.data.TimeRepository
import com.neilturner.overlayparty.data.WeatherRepository
import com.neilturner.overlayparty.ui.overlay.IconPosition
import com.neilturner.overlayparty.ui.overlay.OverlayAnimationType
import com.neilturner.overlayparty.ui.overlay.OverlayContent
import com.neilturner.overlayparty.ui.overlay.OverlayItem
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
        .map { weather ->
            val icon = when (weather.condition) {
                "Sunny" -> Icons.Filled.WbSunny
                "Cloudy" -> Icons.Filled.Cloud
                "Rainy" -> Icons.Filled.WaterDrop
                "Snowy" -> Icons.Filled.AcUnit
                else -> Icons.Filled.Cloud
            }
            
            OverlayContent.MultiItemContent(
                items = listOf(
                    OverlayItem.Text(weather.city),
                    OverlayItem.Icon(icon),
                    OverlayItem.Text(weather.temperature)
                ),
                animationType = OverlayAnimationType.FADE_AND_REPLACE
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OverlayContent.TextOnly("Loading Weather...", OverlayAnimationType.FADE_AND_REPLACE)
        )

    val topEndOverlay: StateFlow<OverlayContent?> = timeRepository.getTimeStream()
        .map { OverlayContent.TextOnly(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OverlayContent.TextOnly("Loading Time...")
        )

    val bottomStartOverlay: StateFlow<OverlayContent?> = musicRepository.getMusicStream()
        .map { 
            OverlayContent.IconWithText(
                text = it, 
                icon = Icons.Default.MusicNote,
                iconPosition = IconPosition.LEADING
            ) 
        }
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
