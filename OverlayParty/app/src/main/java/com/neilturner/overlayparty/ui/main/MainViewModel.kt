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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    weatherRepository: WeatherRepository,
    timeRepository: TimeRepository,
    musicRepository: MusicRepository,
    locationRepository: LocationRepository,
    messageRepository: MessageRepository
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
                animationType = OverlayAnimationType.FADE_AND_REPLACE,
                padding = 4.dp
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OverlayContent.TextOnly("Loading Weather...", animationType = OverlayAnimationType.FADE_AND_REPLACE)
        )

    val topEndOverlay: StateFlow<OverlayContent?> = timeRepository.getTimeStream(showSeconds = false)
        .map { dateTime ->
            OverlayContent.VerticalStack(
                items = listOf(
                    OverlayContent.TextOnly(dateTime.date, padding = 4.dp),
                    OverlayContent.TextOnly(dateTime.time, scale = 2f, padding = 0.dp)
                )
            )
        }
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
                iconPosition = IconPosition.TRAILING
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OverlayContent.IconWithText("Loading Music...", Icons.Default.MusicNote)
        )

    val bottomEndOverlay: StateFlow<OverlayContent?> = combine(
        locationRepository.getLocationStream(),
        messageRepository.getMessageStream()
    ) { location, message ->
        OverlayContent.VerticalStack(
            items = listOf(
                OverlayContent.TextOnly(message, animationType = OverlayAnimationType.FADE_AND_REPLACE),
                OverlayContent.TextOnly(location, animationType = OverlayAnimationType.FADE_AND_REPLACE)
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OverlayContent.TextOnly("Loading Location...")
    )
}