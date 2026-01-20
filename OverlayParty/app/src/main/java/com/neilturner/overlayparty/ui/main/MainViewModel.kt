package com.neilturner.overlayparty.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.overlayparty.data.LocationRepository
import com.neilturner.overlayparty.data.MessageRepository
import com.neilturner.overlayparty.data.MusicRepository
import com.neilturner.overlayparty.data.TimeRepository
import com.neilturner.overlayparty.data.WeatherRepository
import com.neilturner.overlayparty.ui.overlay.IconPosition
import com.neilturner.overlayparty.ui.overlay.OverlayAnimationType
import com.neilturner.overlayparty.ui.overlay.OverlayContent
import com.neilturner.overlayparty.ui.overlay.OverlayItem
import com.neilturner.overlayparty.ui.overlay.OverlayPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class MainViewModel(
    weatherRepository: WeatherRepository,
    timeRepository: TimeRepository,
    musicRepository: MusicRepository,
    locationRepository: LocationRepository,
    messageRepository: MessageRepository
) : ViewModel() {

    // Default all overlays to visible
    private val _visibleOverlays = MutableStateFlow(
        setOf(
            OverlayPosition.TOP_START,
            OverlayPosition.TOP_END,
            OverlayPosition.BOTTOM_START,
            OverlayPosition.BOTTOM_END
        )
    )

    fun setOverlayVisibility(position: OverlayPosition, isVisible: Boolean) {
        _visibleOverlays.update { current ->
            if (isVisible) current + position else current - position
        }
    }

    fun toggleOverlay(position: OverlayPosition) {
        _visibleOverlays.update { current ->
            if (current.contains(position)) current - position else current + position
        }
    }

    val topStartOverlay: StateFlow<OverlayContent?> = combine(
        weatherRepository.getWeatherStream(),
        _visibleOverlays
    ) { weather, visibleOverlays ->
        if (!visibleOverlays.contains(OverlayPosition.TOP_START)) {
            null
        } else {
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
                animationType = OverlayAnimationType.NONE,
                padding = 12.dp
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OverlayContent.TextOnly("Loading Weather...")
    )

    val topEndOverlay: StateFlow<OverlayContent?> = combine(
        timeRepository.getTimeStream(showSeconds = true),
        _visibleOverlays
    ) { dateTime, visibleOverlays ->
        if (!visibleOverlays.contains(OverlayPosition.TOP_END)) {
            null
        } else {
            OverlayContent.VerticalStack(
                items = listOf(
                    OverlayContent.TextOnly(dateTime.date, padding = 4.dp, animationType = OverlayAnimationType.NONE),
                    OverlayContent.TextOnly(dateTime.time, scale = 2f, padding = 4.dp, animationType = OverlayAnimationType.NONE)
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OverlayContent.TextOnly("Loading Time...")
    )

    val bottomStartOverlay: StateFlow<OverlayContent?> = combine(
        musicRepository.getMusicStream(),
        _visibleOverlays
    ) { music, visibleOverlays ->
        if (!visibleOverlays.contains(OverlayPosition.BOTTOM_START)) {
            null
        } else {
            OverlayContent.IconWithText(
                text = music, 
                icon = Icons.Default.MusicNote,
                iconPosition = IconPosition.LEADING,
                animationType = OverlayAnimationType.NONE
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OverlayContent.IconWithText("Loading Music...", Icons.Default.MusicNote)
    )

    val bottomEndOverlay: StateFlow<OverlayContent?> = combine(
        locationRepository.getLocationStream(),
        messageRepository.getMessageStream(),
        _visibleOverlays
    ) { location, message, visibleOverlays ->
        if (!visibleOverlays.contains(OverlayPosition.BOTTOM_END)) {
            null
        } else {
            OverlayContent.VerticalStack(
                items = listOf(
                    OverlayContent.TextOnly(message, animationType = OverlayAnimationType.NONE),
                    OverlayContent.TextOnly(location, animationType = OverlayAnimationType.NONE)
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OverlayContent.TextOnly("Loading Location...")
    )
}