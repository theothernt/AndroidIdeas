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
import com.neilturner.overlayparty.data.CountdownRepository
import com.neilturner.overlayparty.data.DateTimeInfo
import com.neilturner.overlayparty.data.LocationRepository
import com.neilturner.overlayparty.data.MessageRepository
import com.neilturner.overlayparty.data.MusicRepository
import com.neilturner.overlayparty.data.TimeRepository
import com.neilturner.overlayparty.data.WeatherInfo
import com.neilturner.overlayparty.data.WeatherRepository
import com.neilturner.overlayparty.ui.overlay.IconPosition
import com.neilturner.overlayparty.ui.overlay.OverlayAnimationType
import com.neilturner.overlayparty.ui.overlay.OverlayContent
import com.neilturner.overlayparty.ui.overlay.OverlayItem
import com.neilturner.overlayparty.ui.overlay.OverlayPosition
import com.neilturner.overlayparty.ui.overlay.StackAlignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
) : ViewModel() {
    companion object {
        const val VISIBLE_DURATION_MS: Long = 5_000L
        const val FADE_OUT_DURATION_MS: Long = 1000L
        const val FADE_IN_DURATION_MS: Long = 1000L
    }
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

    private val _isOverlaysVisible = MutableStateFlow(true)
    val isOverlaysVisible: StateFlow<Boolean> = _isOverlaysVisible.asStateFlow()

    private val _groupAlpha = MutableStateFlow(1f)
    val groupAlpha: StateFlow<Float> = _groupAlpha.asStateFlow()

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
                Icons.Default.MusicNote,
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
    private var hasInitialFlushOccurred = false

    init {
        viewModelScope.launch {
            launch {
                weatherRepository.getWeatherStream().collect {
                    latestWeather = it
                    if (!hasInitialFlushOccurred) updateTopStart()
                }
            }
            launch {
                timeRepository.getTimeStream(showSeconds = false).collect {
                    latestDateTime = it
                    if (!hasInitialFlushOccurred) updateTopEnd()
                }
            }
            launch {
                musicRepository.getMusicStream().collect {
                    latestMusic = it
                    if (!hasInitialFlushOccurred) updateBottomStart()
                }
            }
            launch {
                countdownRepository.getCountdownStream(durationMinutes = 2).collect {
                    latestCountdown = it
                    if (!hasInitialFlushOccurred) updateBottomStart()
                }
            }
            launch {
                locationRepository.getLocationStream().collect {
                    latestLocation = it
                    if (!hasInitialFlushOccurred) updateBottomEnd()
                }
            }
            launch {
                messageRepository.getMessageStream().collect {
                    latestMessage = it
                    if (!hasInitialFlushOccurred) updateBottomEnd()
                }
            }

            // Allow initial repository values to arrive before starting cycle
            delay(100.milliseconds)
            hasInitialFlushOccurred = true
            flushAllOverlays()

            // Coordinated fade cycle
            while (isActive) {
                delay(visibleDurationMs.milliseconds)

                // Fade out together
                _isOverlaysVisible.value = false
                _groupAlpha.value = 0f
                delay(fadeOutDurationMs.milliseconds)

                // Update data while completely hidden
                flushAllOverlays()
                delay(50.milliseconds)

                // Fade back in together
                _isOverlaysVisible.value = true
                _groupAlpha.value = 1f
                delay(fadeInDurationMs.milliseconds)
            }
        }
    }

    fun setOverlayVisibility(
        position: OverlayPosition,
        isVisible: Boolean,
    ) {
        _visibleOverlays.update { current ->
            if (isVisible) current + position else current - position
        }
        flushAllOverlays()
    }

    fun toggleOverlay(position: OverlayPosition) {
        _visibleOverlays.update { current ->
            if (current.contains(position)) current - position else current + position
        }
        flushAllOverlays()
    }

    private fun flushAllOverlays() {
        updateTopStart()
        updateTopEnd()
        updateBottomStart()
        updateBottomEnd()
    }

    private fun updateTopStart() {
        val visible = _visibleOverlays.value.contains(OverlayPosition.TOP_START)
        val weather = latestWeather
        _topStartOverlay.value =
            if (!visible || weather == null) {
                null
            } else {
                val icon =
                    when (weather.condition) {
                        "Sunny" -> Icons.Filled.WbSunny
                        "Cloudy" -> Icons.Filled.Cloud
                        "Rainy" -> Icons.Filled.WaterDrop
                        "Snowy" -> Icons.Filled.AcUnit
                        else -> Icons.Filled.Cloud
                    }

                OverlayContent.MultiItemContent(
                    items =
                        listOf(
                            OverlayItem.Text(weather.city),
                            OverlayItem.Icon(icon),
                            OverlayItem.Text(weather.temperature),
                        ),
                    animationType = OverlayAnimationType.NONE,
                    padding = 4.dp,
                )
            }
    }

    private fun updateTopEnd() {
        val visible = _visibleOverlays.value.contains(OverlayPosition.TOP_END)
        val dateTime = latestDateTime
        _topEndOverlay.value =
            if (!visible || dateTime == null) {
                null
            } else {
                OverlayContent.VerticalStack(
                    items =
                        listOf(
                            OverlayContent.TextOnly(dateTime.date, padding = 4.dp, animationType = OverlayAnimationType.NONE),
                            OverlayContent.TextOnly(dateTime.time, scale = 2f, padding = 4.dp, animationType = OverlayAnimationType.NONE),
                        ),
                    alignment = StackAlignment.END,
                )
            }
    }

    private fun updateBottomStart() {
        val visible = _visibleOverlays.value.contains(OverlayPosition.BOTTOM_START)
        val music = latestMusic
        val countdown = latestCountdown
        _bottomStartOverlay.value =
            if (!visible) {
                null
            } else {
                val items =
                    buildList {
                        countdown?.let {
                            add(OverlayContent.TextOnly(it, animationType = OverlayAnimationType.NONE))
                        }
                        music?.let {
                            add(
                                OverlayContent.IconWithText(
                                    text = it,
                                    icon = Icons.Default.MusicNote,
                                    iconPosition = IconPosition.LEADING,
                                    animationType = OverlayAnimationType.NONE,
                                ),
                            )
                        }
                    }
                if (items.isEmpty()) {
                    null
                } else {
                    OverlayContent.VerticalStack(
                        items = items,
                        alignment = StackAlignment.START,
                    )
                }
            }
    }

    private fun updateBottomEnd() {
        val visible = _visibleOverlays.value.contains(OverlayPosition.BOTTOM_END)
        val location = latestLocation
        val message = latestMessage
        _bottomEndOverlay.value =
            if (!visible) {
                null
            } else {
                val items =
                    buildList {
                        message?.let {
                            add(OverlayContent.TextOnly(it, animationType = OverlayAnimationType.NONE))
                        }
                        location?.let {
                            add(OverlayContent.TextOnly(it, animationType = OverlayAnimationType.NONE))
                        }
                    }
                if (items.isEmpty()) {
                    null
                } else {
                    OverlayContent.VerticalStack(
                        items = items,
                    )
                }
            }
    }
}
