package com.neilturner.overlayparty.ui.main

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
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

    private fun visibilityOf(position: OverlayPosition): Flow<Boolean> =
        visibility.visibleOverlays
            .map { it.contains(position) }
            .distinctUntilChanged()

    val topStartOverlay =
        visibilityOf(OverlayPosition.TOP_START)
            .flatMapLatest { visible ->
                if (visible) {
                    weatherRepository.getWeatherStream().map { weatherMapper(it) }
                } else {
                    flowOf<OverlayContent?>(null)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = OverlayContent.TextOnly("Loading Weather..."),
            )

    val topEndOverlay =
        visibilityOf(OverlayPosition.TOP_END)
            .flatMapLatest { visible ->
                if (visible) {
                    timeRepository.getTimeStream(showSeconds = false).map { timeMapper(it) }
                } else {
                    flowOf<OverlayContent?>(null)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = OverlayContent.TextOnly("Loading Time..."),
            )

    val bottomStartOverlay =
        visibilityOf(OverlayPosition.BOTTOM_START)
            .flatMapLatest { visible ->
                if (visible) {
                    combine(
                        musicRepository.getMusicStream(),
                        countdownRepository.getCountdownStream(durationMinutes = 2),
                    ) { music, countdown -> bottomStartMapper(music, countdown) }
                } else {
                    flowOf<OverlayContent?>(null)
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
        visibilityOf(OverlayPosition.BOTTOM_END)
            .flatMapLatest { visible ->
                if (visible) {
                    combine(
                        locationRepository.getLocationStream(),
                        messageRepository.getMessageStream(),
                    ) { location, message -> bottomEndMapper(location, message) }
                } else {
                    flowOf<OverlayContent?>(null)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = OverlayContent.TextOnly("Loading Location..."),
            )
}
