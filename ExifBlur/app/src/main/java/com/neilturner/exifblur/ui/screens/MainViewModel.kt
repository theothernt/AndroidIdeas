package com.neilturner.exifblur.ui.screens

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.neilturner.exifblur.data.ExifMetadata
import com.neilturner.exifblur.data.ImageRepository
import com.neilturner.exifblur.data.SmbImage
import com.neilturner.exifblur.slideshow.CacheConfig
import com.neilturner.exifblur.slideshow.SlideshowController
import com.neilturner.exifblur.slideshow.SlideshowPreloader
import com.neilturner.exifblur.slideshow.currentDisplaySize
import com.neilturner.exifblur.util.ExifMetadataLoader
import com.neilturner.exifblur.util.LocationHelper
import com.neilturner.exifblur.util.RamInfo
import com.neilturner.exifblur.util.RamMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(
    private val imageRepository: ImageRepository,
    private val locationHelper: LocationHelper,
    private val ramMonitor: RamMonitor,
    private val metadataLoader: ExifMetadataLoader,
    private val imageLoader: ImageLoader,
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var ramMonitoringJob: Job? = null
    private var started = false
    private var playlist: List<SmbImage> = emptyList()
    private var preloader: SlideshowPreloader? = null
    private var controller: SlideshowController? = null

    fun start() {
        if (started) return
        started = true

        val loader =
            SlideshowPreloader(
                imageLoader = imageLoader,
                scope = viewModelScope,
                context = appContext,
                targetSize = currentDisplaySize(appContext),
                windowSize = CacheConfig.prefetchWindow,
                keepPrevious = CacheConfig.keepPrevious,
            )
        val slideshow = SlideshowController(loader, viewModelScope)
        preloader = loader
        controller = slideshow

        viewModelScope.launch {
            val listing = imageRepository.getImages()
            val items = listing.uris.map(::SmbImage)
            playlist = items
            loader.setPlaylist(items)
            _uiState.update {
                it.copy(
                    playlist = items,
                    imageCount = items.size,
                    imageSource = listing.sourceName,
                    error = listing.error,
                )
            }
            slideshow.start()
        }

        viewModelScope.launch {
            slideshow.currentIndex.collect { index ->
                _uiState.update { it.copy(currentIndex = index) }
                val item = playlist.getOrNull(index)
                val metadata = item?.let { metadataLoader.load(it.uri) }
                _uiState.update { it.copy(metadataLabel = resolveLocationOrModel(metadata)) }
            }
        }

        viewModelScope.launch {
            slideshow.isStarting.collect { starting ->
                _uiState.update { it.copy(isStarting = starting) }
            }
        }

        viewModelScope.launch {
            slideshow.error.collect { error ->
                if (error != null) _uiState.update { it.copy(error = error) }
            }
        }

        viewModelScope.launch {
            loader.isPrefetching.collect { active ->
                _uiState.update { it.copy(isPrefetching = active) }
            }
        }

        startRamMonitoring()
    }

    internal suspend fun resolveLocationOrModel(exif: ExifMetadata?): String? {
        if (exif == null) return null

        val resolveStartTime = System.currentTimeMillis()
        val locationLabel =
            if (exif.latitude != null && exif.longitude != null) {
                locationHelper.getAddressFromLocation(exif.latitude, exif.longitude)
            } else {
                null
            }

        if (locationLabel != null) {
            Log.d(
                "MainViewModel",
                "Reverse geocoding took ${System.currentTimeMillis() - resolveStartTime}ms",
            )
        }

        val parts = mutableListOf<String>()
        locationLabel?.let { parts.add(it) }

        exif.date?.let { rawDate ->
            val formattedDate =
                try {
                    val components = rawDate.split(" ")[0].split(":")
                    if (components.size == 3) {
                        val year = components[0]
                        val month = components[1].toInt()
                        val day = components[2].toInt()
                        val monthNames =
                            arrayOf(
                                "Jan",
                                "Feb",
                                "Mar",
                                "Apr",
                                "May",
                                "Jun",
                                "Jul",
                                "Aug",
                                "Sep",
                                "Oct",
                                "Nov",
                                "Dec",
                            )
                        val dateOnly = "$day ${monthNames[month - 1]} $year"
                        val timePart = rawDate.split(" ").getOrNull(1)
                        if (timePart != null) {
                            val timeAndOffset =
                                if (exif.offset != null) "$timePart (${exif.offset})" else timePart
                            "$dateOnly $timeAndOffset"
                        } else {
                            dateOnly
                        }
                    } else {
                        rawDate
                    }
                } catch (e: Exception) {
                    rawDate
                }
            parts.add(formattedDate)
        }
        return if (parts.isNotEmpty()) parts.joinToString(" • ") else null
    }

    private fun startRamMonitoring() {
        ramMonitoringJob?.cancel()
        ramMonitoringJob =
            viewModelScope.launch {
                _uiState.update { it.copy(ramInfo = ramMonitor.getCurrentRamUsage()) }
                while (isActive) {
                    delay(RAM_UPDATE_INTERVAL)
                    _uiState.update { it.copy(ramInfo = ramMonitor.getCurrentRamUsage()) }
                }
            }
    }

    private fun stopRamMonitoring() {
        ramMonitoringJob?.cancel()
        ramMonitoringJob = null
    }

    fun toggleOverlays() {
        val showingOverlays = !_uiState.value.areOverlaysVisible
        _uiState.update { it.copy(areOverlaysVisible = showingOverlays) }
        if (showingOverlays) {
            startRamMonitoring()
        } else {
            stopRamMonitoring()
        }
    }

    override fun onCleared() {
        controller?.stop()
        preloader?.close()
        stopRamMonitoring()
        super.onCleared()
    }

    private companion object {
        const val RAM_UPDATE_INTERVAL = 500L
    }
}

data class MainUiState(
    val playlist: List<SmbImage> = emptyList(),
    val currentIndex: Int = -1,
    val isStarting: Boolean = true,
    val error: String? = null,
    val metadataLabel: String? = null,
    val imageCount: Int = 0,
    val imageSource: String = "",
    val isPrefetching: Boolean = false,
    val areOverlaysVisible: Boolean = true,
    val ramInfo: RamInfo? = null,
)
