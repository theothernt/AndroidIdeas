package com.neilturner.exifblur.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.exifblur.data.ExifMetadata
import com.neilturner.exifblur.data.ImageRepository
import com.neilturner.exifblur.util.BitmapHelper
import com.neilturner.exifblur.util.BitmapResult
import com.neilturner.exifblur.util.LocationHelper
import com.neilturner.exifblur.util.RamMonitor
import com.neilturner.exifblur.util.RamInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(
    private val imageRepository: ImageRepository,
    private val locationHelper: LocationHelper,
    private val bitmapHelper: BitmapHelper,
    private val ramMonitor: RamMonitor
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var slideshowJob: Job? = null
    private var ramMonitoringJob: Job? = null
    private var loadImagesJob: Job? = null
    private var preloadJob: Job? = null

    // Cache for preloaded bitmaps: index -> BitmapResult
    private val preloadedBitmaps = mutableMapOf<Int, BitmapResult>()

    companion object {
        const val TRANSITION_DURATION = 2000 // Image crossfade duration (ms)
        const val DISPLAY_DURATION = 10000L // How long to show image before transitioning
        const val PRELOAD_TRIGGER_TIME = 3000L // Start preloading this many ms before next image
        const val RAM_UPDATE_INTERVAL = 500L // Update RAM usage every second
    }

    fun updatePermissionStatus(granted: Boolean) {
        val previousGranted = _uiState.value.hasPermission
        _uiState.value = _uiState.value.copy(
            hasPermission = granted,
            isPermissionCheckComplete = true,
            transitionDuration = TRANSITION_DURATION
        )

        if (!granted) {
            loadImagesJob?.cancel()
            slideshowJob?.cancel()
            preloadJob?.cancel()
            preloadedBitmaps.clear()
            ramMonitoringJob?.cancel()
            return
        }

        if (!previousGranted) {
            loadImages()
            startRamMonitoring()
        }
    }

    private fun loadImages() {
        loadImagesJob?.cancel()
        loadImagesJob = viewModelScope.launch {
            Log.d("MainViewModel", "Starting image loading process...")
            val startTime = System.currentTimeMillis()
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val fetchUrisStartTime = System.currentTimeMillis()
            val uris = imageRepository.getImages()
            val fetchUrisDuration = System.currentTimeMillis() - fetchUrisStartTime
            Log.d("MainViewModel", "Fetched ${uris.size} image URIs in ${fetchUrisDuration}ms")
            if (uris.isEmpty()) {
                Log.w("MainViewModel", "No images found! Check your source settings.")
            }
            
            // Create LoadedImage objects without pre-loading metadata since BitmapHelper will handle it
            val loadedImages = uris.mapIndexed { index, uri ->
                if ((index + 1) % 10 == 0 || index == 0 || index == uris.size - 1) {
                    Log.d("MainViewModel", "Processing for image ${index + 1}/${uris.size}")
                }
                LoadedImage(uri = uri, metadataLabel = null)
            }
            
            Log.d("MainViewModel", "Created ${loadedImages.size} LoadedImage objects")

            // Load initial bitmap (this will also load EXIF data)
            val bitmapStartTime = System.currentTimeMillis()
            val initialResult = if (loadedImages.isNotEmpty()) {
                Log.d("MainViewModel", "Attempting to load initial bitmap for: ${loadedImages[0].uri}")
                bitmapHelper.loadResizedBitmap(loadedImages[0].uri)
            } else {
                Log.w("MainViewModel", "Cannot load initial bitmap: loadedImages is empty")
                null
            }
            
            val initialMetadataLabel = initialResult?.metadata?.let { resolveLocationOrModel(it) }

            val bitmapDuration = System.currentTimeMillis() - bitmapStartTime
//            if (initialResult != null) {
//                Log.d("MainViewModel", "Loaded initial bitmap with metadata in ${bitmapDuration}ms")
//            }

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d("MainViewModel", "Image loading process complete. Total images: ${loadedImages.size}, Total time: ${totalDuration}ms")

            _uiState.value = _uiState.value.copy(
                imageCount = loadedImages.size,
                images = loadedImages,
                isLoading = false,
                currentImageIndex = 0,
                currentDisplayImage = initialResult?.let { DisplayImage(it.bitmap, initialMetadataLabel, it.rotation) },
                areOverlaysVisible = false,
                imageSource = imageRepository.getSourceName()
            )
            
            if (loadedImages.isNotEmpty()) {
                startSlideshow()
            }
        }
    }

    private fun startSlideshow() {
        slideshowJob?.cancel()
        preloadJob?.cancel()
        slideshowJob = viewModelScope.launch {
            while (isActive) {
                val currentState = _uiState.value
                if (currentState.images.isNotEmpty()) {
                    // Calculate next index
                    val nextIndex = (currentState.currentImageIndex + 1) % currentState.images.size
                    val nextImage = currentState.images[nextIndex]

                    // 1. Wait until preload trigger time (DISPLAY_DURATION - PRELOAD_TRIGGER_TIME)
                    val preloadWaitTime = DISPLAY_DURATION - PRELOAD_TRIGGER_TIME
                    delay(preloadWaitTime)

                    // 2. Start preloading next image in background
                    preloadJob?.cancel()
                    preloadJob = launch {
                        Log.d("MainViewModel", "Starting preload for image at index $nextIndex")
                        val result = bitmapHelper.loadResizedBitmap(nextImage.uri)
                        if (result != null) {
                            preloadedBitmaps[nextIndex] = result
                            Log.d("MainViewModel", "Preload complete for index $nextIndex")
                        }
                    }

                    // 3. Wait remaining time until image switch
                    delay(PRELOAD_TRIGGER_TIME)

                    // 4. Use preloaded bitmap if available, otherwise load synchronously as fallback
                    val result = preloadedBitmaps.remove(nextIndex) ?: run {
                        Log.w("MainViewModel", "Preload not ready for index $nextIndex, loading synchronously")
                        bitmapHelper.loadResizedBitmap(nextImage.uri)
                    }

                    val resolveStartTime = System.currentTimeMillis()
                    val metadataLabel = result?.metadata?.let { resolveLocationOrModel(it) }
                    // Log.d("MainViewModel", "Metadata resolution took ${System.currentTimeMillis() - resolveStartTime}ms for index $nextIndex")

                    // 5. Switch image (starts crossfade)
                    _uiState.update {
                        it.copy(
                            currentImageIndex = nextIndex,
                            currentDisplayImage = result?.let { res -> DisplayImage(res.bitmap, metadataLabel, res.rotation) }
                        )
                    }

                    // 6. Wait for image transition to complete
                    delay(TRANSITION_DURATION.toLong())
                } else {
                    delay(DISPLAY_DURATION)
                }
            }
        }
    }

    internal suspend fun resolveLocationOrModel(exif: ExifMetadata?): String? {
        if (exif == null) return null
        
        val resolveStartTime = System.currentTimeMillis()
        val locationLabel = if (exif.latitude != null && exif.longitude != null) {
            locationHelper.getAddressFromLocation(exif.latitude, exif.longitude)
        } else null
        
        if (locationLabel != null) {
            Log.d("MainViewModel", "Reverse geocoding took ${System.currentTimeMillis() - resolveStartTime}ms")
        }

        val parts = mutableListOf<String>()
        locationLabel?.let { parts.add(it) }

        exif.date?.let { rawDate ->
            // EXIF date is usually "YYYY:MM:DD HH:MM:SS"
            val formattedDate = try {
                val components = rawDate.split(" ")[0].split(":")
                if (components.size == 3) {
                    val year = components[0]
                    val month = components[1].toInt()
                    val day = components[2].toInt()
                    val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    val dateOnly = "$day ${monthNames[month - 1]} $year"
                    
                    // Add time and offset if available
                    val timePart = rawDate.split(" ").getOrNull(1)
                    if (timePart != null) {
                        val timeAndOffset = if (exif.offset != null) "$timePart (${exif.offset})" else timePart
                        "$dateOnly $timeAndOffset"
                    } else {
                        dateOnly
                    }
                } else rawDate
            } catch (e: Exception) {
                rawDate
            }
            parts.add(formattedDate)
        }
        return if (parts.isNotEmpty()) parts.joinToString(" • ") else null
    }

    private fun startRamMonitoring() {
        ramMonitoringJob?.cancel()
        ramMonitoringJob = viewModelScope.launch {
            while (isActive) {
                val ramInfo = ramMonitor.getCurrentRamUsage()
                _uiState.update { it.copy(ramInfo = ramInfo) }
                delay(RAM_UPDATE_INTERVAL)
            }
        }
    }

    fun toggleOverlays() {
        _uiState.update { it.copy(areOverlaysVisible = !it.areOverlaysVisible) }
    }

    override fun onCleared() {
        super.onCleared()
        preloadJob?.cancel()
        preloadedBitmaps.clear()
    }
}

data class LoadedImage(
    val uri: Uri,
    val metadataLabel: String?
)

data class DisplayImage(
    val bitmap: Bitmap,
    val metadataLabel: String?,
    val rotation: Float
)

data class MainUiState(
    val name: String = "Android",
    val hasPermission: Boolean = false,
    val isPermissionCheckComplete: Boolean = false,
    val imageCount: Int = 0,
    val isLoading: Boolean = false,
    val images: List<LoadedImage> = emptyList(),
    val currentImageIndex: Int = 0,
    val currentDisplayImage: DisplayImage? = null,
    val areOverlaysVisible: Boolean = false,
    val transitionDuration: Int = 1000,
    val ramInfo: RamInfo? = null,
    val imageSource: String = ""
)
