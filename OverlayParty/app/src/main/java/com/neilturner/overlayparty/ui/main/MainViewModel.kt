package com.neilturner.overlayparty.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.overlayparty.data.CountdownRepository
import com.neilturner.overlayparty.data.LocationRepository
import com.neilturner.overlayparty.data.MessageRepository
import com.neilturner.overlayparty.data.MusicRepository
import com.neilturner.overlayparty.data.TimeRepository
import com.neilturner.overlayparty.data.WeatherRepository
import com.neilturner.overlayparty.domain.overlay.BottomStartOverlayContentUseCase
import com.neilturner.overlayparty.domain.overlay.LocationMessageToOverlayContentUseCase
import com.neilturner.overlayparty.domain.overlay.MusicToOverlayContentUseCase
import com.neilturner.overlayparty.domain.overlay.OverlayVisibilityManager
import com.neilturner.overlayparty.domain.overlay.TimeToOverlayContentUseCase
import com.neilturner.overlayparty.domain.overlay.WeatherToOverlayContentUseCase
import com.neilturner.overlayparty.ui.overlay.OverlayContent
import com.neilturner.overlayparty.ui.overlay.OverlayIcon
import com.neilturner.overlayparty.ui.overlay.OverlayPosition
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    weatherRepository: WeatherRepository,
    timeRepository: TimeRepository,
    musicRepository: MusicRepository,
    locationRepository: LocationRepository,
    messageRepository: MessageRepository,
    countdownRepository: CountdownRepository,
    private val visibility: OverlayVisibilityManager = OverlayVisibilityManager(),
    private val weatherMapper: WeatherToOverlayContentUseCase = WeatherToOverlayContentUseCase(),
    private val timeMapper: TimeToOverlayContentUseCase = TimeToOverlayContentUseCase(),
    private val bottomStartMapper: BottomStartOverlayContentUseCase =
        BottomStartOverlayContentUseCase(MusicToOverlayContentUseCase()),
    private val bottomEndMapper: LocationMessageToOverlayContentUseCase = LocationMessageToOverlayContentUseCase(),
) : ViewModel() {
    val visibleOverlays = visibility.visibleOverlays

    fun setOverlayVisibility(
        position: OverlayPosition,
        isVisible: Boolean,
    ) = visibility.setOverlayVisibility(position, isVisible)

    fun toggleOverlay(position: OverlayPosition) = visibility.toggleOverlay(position)

    val topStartOverlay =
        combine(
            weatherRepository.getWeatherStream(),
            visibility.visibleOverlays,
        ) { weather, visibleOverlays ->
            if (!visibleOverlays.contains(OverlayPosition.TOP_START)) {
                null
            } else {
                weatherMapper(weather)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OverlayContent.TextOnly("Loading Weather..."),
        )

    val topEndOverlay =
        combine(
            timeRepository.getTimeStream(showSeconds = false),
            visibility.visibleOverlays,
        ) { dateTime, visibleOverlays ->
            if (!visibleOverlays.contains(OverlayPosition.TOP_END)) {
                null
            } else {
                timeMapper(dateTime)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OverlayContent.TextOnly("Loading Time..."),
        )

    val bottomStartOverlay =
        combine(
            musicRepository.getMusicStream(),
            countdownRepository.getCountdownStream(durationMinutes = 2),
            visibility.visibleOverlays,
        ) { music, countdown, visibleOverlays ->
            if (!visibleOverlays.contains(OverlayPosition.BOTTOM_START)) {
                null
            } else {
                bottomStartMapper(music, countdown)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue =
OverlayContent.IconWithText(
                     "Loading Music...",
                     OverlayIcon.MusicNote,
                 ),
        )

    val bottomEndOverlay =
        combine(
            locationRepository.getLocationStream(),
            messageRepository.getMessageStream(),
            visibility.visibleOverlays,
        ) { location, message, visibleOverlays ->
            if (!visibleOverlays.contains(OverlayPosition.BOTTOM_END)) {
                null
            } else {
                bottomEndMapper(location, message)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OverlayContent.TextOnly("Loading Location..."),
        )
}