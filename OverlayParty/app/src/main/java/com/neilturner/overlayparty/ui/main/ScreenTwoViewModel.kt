package com.neilturner.overlayparty.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.overlayparty.data.CountdownRepository
import com.neilturner.overlayparty.data.DateTimeInfo
import com.neilturner.overlayparty.data.LocationRepository
import com.neilturner.overlayparty.data.MessageRepository
import com.neilturner.overlayparty.data.MusicRepository
import com.neilturner.overlayparty.data.TimeRepository
import com.neilturner.overlayparty.data.WeatherInfo
import com.neilturner.overlayparty.data.WeatherRepository
import com.neilturner.overlayparty.domain.overlay.BottomStartOverlayContentUseCase
import com.neilturner.overlayparty.domain.overlay.LocationMessageToOverlayContentUseCase
import com.neilturner.overlayparty.domain.overlay.MusicToOverlayContentUseCase
import com.neilturner.overlayparty.domain.overlay.OverlayVisibilityManager
import com.neilturner.overlayparty.domain.overlay.TimeToOverlayContentUseCase
import com.neilturner.overlayparty.domain.overlay.WeatherToOverlayContentUseCase
import com.neilturner.overlayparty.ui.overlay.OverlayAnimationType
import com.neilturner.overlayparty.ui.overlay.OverlayContent
import com.neilturner.overlayparty.ui.overlay.OverlayIcon
import com.neilturner.overlayparty.ui.overlay.OverlayPosition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class ScreenTwoViewModel(
    private val weatherRepository: WeatherRepository,
    private val timeRepository: TimeRepository,
    private val musicRepository: MusicRepository,
    private val locationRepository: LocationRepository,
    private val messageRepository: MessageRepository,
    private val countdownRepository: CountdownRepository,
    private val visibleDurationMs: Long = VISIBLE_DURATION_MS,
    private val fadeOutDurationMs: Long = FADE_OUT_DURATION_MS,
    private val fadeInDurationMs: Long = FADE_IN_DURATION_MS,
    private val visibility: OverlayVisibilityManager = OverlayVisibilityManager(),
    private val weatherMapper: WeatherToOverlayContentUseCase = WeatherToOverlayContentUseCase(),
    private val timeMapper: TimeToOverlayContentUseCase = TimeToOverlayContentUseCase(),
    private val bottomStartMapper: BottomStartOverlayContentUseCase =
        BottomStartOverlayContentUseCase(MusicToOverlayContentUseCase()),
    private val bottomEndMapper: LocationMessageToOverlayContentUseCase = LocationMessageToOverlayContentUseCase(),
    scope: CoroutineScope? = null,
) : ViewModel() {
    companion object {
        const val VISIBLE_DURATION_MS: Long = 5_000L
        const val FADE_OUT_DURATION_MS: Long = 1000L
        const val FADE_IN_DURATION_MS: Long = 1000L
    }

    val visibleOverlays: StateFlow<Set<OverlayPosition>> = visibility.visibleOverlays

    private val _isOverlaysVisible = MutableStateFlow(true)
    val isOverlaysVisible: StateFlow<Boolean> = _isOverlaysVisible.asStateFlow()

    private val _topStartOverlay =
        MutableStateFlow<OverlayContent?>(
            OverlayContent.TextOnly("Loading Weather...", animationType = OverlayAnimationType.NONE),
        )
    val topStartOverlay: StateFlow<OverlayContent?> = _topStartOverlay.asStateFlow()

    private val _topEndOverlay =
        MutableStateFlow<OverlayContent?>(
            OverlayContent.TextOnly("Loading Time...", animationType = OverlayAnimationType.NONE),
        )
    val topEndOverlay: StateFlow<OverlayContent?> = _topEndOverlay.asStateFlow()

    private val _bottomStartOverlay =
        MutableStateFlow<OverlayContent?>(
            OverlayContent.IconWithText(
                "Loading Music...",
                OverlayIcon.MusicNote,
                animationType = OverlayAnimationType.NONE,
            ),
        )
    val bottomStartOverlay: StateFlow<OverlayContent?> = _bottomStartOverlay.asStateFlow()

    private val _bottomEndOverlay =
        MutableStateFlow<OverlayContent?>(
            OverlayContent.TextOnly("Loading Location...", animationType = OverlayAnimationType.NONE),
        )
    val bottomEndOverlay: StateFlow<OverlayContent?> = _bottomEndOverlay.asStateFlow()

    // Buffered repository data
    private var latestWeather: WeatherInfo? = null
    private var latestDateTime: DateTimeInfo? = null
    private var latestMusic: String? = null
    private var latestCountdown: String? = null
    private var latestLocation: String? = null
    private var latestMessage: String? = null

    init {
        val coroutineScope = scope ?: viewModelScope
        coroutineScope.launch {
            val weatherReady = CompletableDeferred<Unit>()
            val timeReady = CompletableDeferred<Unit>()
            val musicReady = CompletableDeferred<Unit>()
            val countdownReady = CompletableDeferred<Unit>()
            val locationReady = CompletableDeferred<Unit>()
            val messageReady = CompletableDeferred<Unit>()
            var isInitialFlushPending = true

            launch {
                weatherRepository.getWeatherStream().collect {
                    latestWeather = it
                    if (isInitialFlushPending) updateTopStart()
                    weatherReady.complete(Unit)
                }
            }
            launch {
                timeRepository.getTimeStream(showSeconds = false).collect {
                    latestDateTime = it
                    if (isInitialFlushPending) updateTopEnd()
                    timeReady.complete(Unit)
                }
            }
            launch {
                musicRepository.getMusicStream().collect {
                    latestMusic = it
                    if (isInitialFlushPending) updateBottomStart()
                    musicReady.complete(Unit)
                }
            }
            launch {
                countdownRepository.getCountdownStream(durationMinutes = 2).collect {
                    latestCountdown = it
                    if (isInitialFlushPending) updateBottomStart()
                    countdownReady.complete(Unit)
                }
            }
            launch {
                locationRepository.getLocationStream().collect {
                    latestLocation = it
                    if (isInitialFlushPending) updateBottomEnd()
                    locationReady.complete(Unit)
                }
            }
            launch {
                messageRepository.getMessageStream().collect {
                    latestMessage = it
                    if (isInitialFlushPending) updateBottomEnd()
                    messageReady.complete(Unit)
                }
            }

            // Do not start the cycle until every source has produced a first value
            awaitAll(weatherReady, timeReady, musicReady, countdownReady, locationReady, messageReady)
            isInitialFlushPending = false
            flushAllOverlays()

            // Coordinated fade cycle
            while (isActive) {
                delay(visibleDurationMs.milliseconds)

                // Fade out together
                _isOverlaysVisible.value = false
                delay(fadeOutDurationMs.milliseconds)

                // Update data while completely hidden
                flushAllOverlays()

                // Fade back in together
                _isOverlaysVisible.value = true
                delay(fadeInDurationMs.milliseconds)
            }
        }
    }

    fun setOverlayVisibility(
        position: OverlayPosition,
        isVisible: Boolean,
    ) {
        visibility.setOverlayVisibility(position, isVisible)
        flushAllOverlays()
    }

    fun toggleOverlay(position: OverlayPosition) {
        visibility.toggleOverlay(position)
        flushAllOverlays()
    }

    private fun flushAllOverlays() {
        updateTopStart()
        updateTopEnd()
        updateBottomStart()
        updateBottomEnd()
    }

    private fun updateTopStart() {
        val visible = visibility.visibleOverlays.value.contains(OverlayPosition.TOP_START)
        val weather = latestWeather
        _topStartOverlay.value =
            if (!visible || weather == null) {
                null
            } else {
                weatherMapper(weather, OverlayAnimationType.NONE)
            }
    }

    private fun updateTopEnd() {
        val visible = visibility.visibleOverlays.value.contains(OverlayPosition.TOP_END)
        val dateTime = latestDateTime
        _topEndOverlay.value =
            if (!visible || dateTime == null) {
                null
            } else {
                timeMapper(dateTime, OverlayAnimationType.NONE)
            }
    }

    private fun updateBottomStart() {
        val visible = visibility.visibleOverlays.value.contains(OverlayPosition.BOTTOM_START)
        val music = latestMusic
        val countdown = latestCountdown
        _bottomStartOverlay.value =
            if (!visible) {
                null
            } else {
                bottomStartMapper(
                    music,
                    countdown,
                    countdownAnimationType = OverlayAnimationType.NONE,
                    musicAnimationType = OverlayAnimationType.NONE,
                )
            }
    }

    private fun updateBottomEnd() {
        val visible = visibility.visibleOverlays.value.contains(OverlayPosition.BOTTOM_END)
        val location = latestLocation
        val message = latestMessage
        _bottomEndOverlay.value =
            if (!visible) {
                null
            } else {
                bottomEndMapper(location, message, OverlayAnimationType.NONE)
            }
    }
}
