package com.neilturner.exifblur.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.exifblur.data.ExifMetadata
import com.neilturner.exifblur.data.ImageRepository
import com.neilturner.exifblur.util.BitmapLoader
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
    private val bitmapLoader: BitmapLoader,
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
        const val DEFAULT_TARGET_WIDTH = 1920
        const val DEFAULT_TARGET_HEIGHT = 1080
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
            // The slideshow is stopped, so nothing is drawing these any more
            preloadedBitmaps.values.forEach { recycle(it.bitmap) }
            preloadedBitmaps.clear()
            ramMonitoringJob?.cancel()
            return
        }

        if (!previousGranted) {
            loadImages()
        }
    }

    private fun loadImages() {
        loadImagesJob?.cancel()
        loadImagesJob = viewModelScope.launch {
            Log.d("MainViewModel", "=========================================")
            Log.d("MainViewModel", "Starting image loading process...")
            val startTime = System.currentTimeMillis()
            _uiState.value = _uiState.value.copy(isLoading = true, imageError = null)

            val fetchUrisStartTime = System.currentTimeMillis()
            val listing = imageRepository.getImages()
            val uris = listing.uris
            val fetchUrisDuration = System.currentTimeMillis() - fetchUrisStartTime
            Log.d("MainViewModel", "[Phase 1] Fetched ${uris.size} image URIs in ${fetchUrisDuration}ms")
            if (uris.isEmpty()) {
                Log.w("MainViewModel", "No images found! ${listing.error ?: "Check your source settings."}")
            }
            
            // Create LoadedImage objects without pre-loading metadata since BitmapHelper will handle it
            val loadedImages = uris.mapIndexed { index, uri ->
                if ((index + 1) % 10 == 0 || index == 0 || index == uris.size - 1) {
                    Log.d("MainViewModel", "Processing for image ${index + 1}/${uris.size}")
                }
                LoadedImage(uri = uri, metadataLabel = null)
            }

            Log.d("MainViewModel", "[Phase 2] Created ${loadedImages.size} LoadedImage objects")

            // Load initial bitmap (this will also load EXIF data)
            val bitmapStartTime = System.currentTimeMillis()
            val initialResult = if (loadedImages.isNotEmpty()) {
                Log.d("MainViewModel", "[Phase 3] Loading initial bitmap + EXIF for image 1/${uris.size}")
                bitmapLoader.loadResizedBitmap(loadedImages[0].uri, DEFAULT_TARGET_WIDTH, DEFAULT_TARGET_HEIGHT)
            } else {
                Log.w("MainViewModel", "Cannot load initial bitmap: loadedImages is empty")
                null
            }

            val initialMetadataLabel = initialResult?.metadata?.let { resolveLocationOrModel(it) }

            val bitmapDuration = System.currentTimeMillis() - bitmapStartTime
            Log.d("MainViewModel", "[Phase 3] Loaded initial bitmap + EXIF in ${bitmapDuration}ms")

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d("MainViewModel", "=========================================")
            Log.d("MainViewModel", "Image loading process complete")
            Log.d("MainViewModel", "  Total images: ${loadedImages.size}")
            Log.d("MainViewModel", "  Total time: ${totalDuration}ms")
            Log.d("MainViewModel", "=========================================")

            _uiState.value = _uiState.value.copy(
                imageCount = loadedImages.size,
                images = loadedImages,
                isLoading = false,
                // If the first image failed to decode, start at -1 so the slideshow loop
                // (which skips undecodable images) attempts image 0 instead of image 1
                currentImageIndex = if (initialResult != null) 0 else -1,
                currentDisplayImage = initialResult?.let { DisplayImage(it.bitmap, initialMetadataLabel) },
                imageSource = listing.sourceName,
                imageError = listing.error
            )
            
            if (loadedImages.isNotEmpty()) {
                startSlideshow()
            }

            // Overlays start visible, so begin polling RAM usage right away
            if (_uiState.value.areOverlaysVisible) {
                startRamMonitoring()
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
                        val preloadStartTime = System.currentTimeMillis()
                        Log.d("MainViewModel", "[Preload] Starting for image at index $nextIndex")
                        val result = bitmapLoader.loadResizedBitmap(nextImage.uri, DEFAULT_TARGET_WIDTH, DEFAULT_TARGET_HEIGHT)
                        if (result != null) {
                            preloadedBitmaps[nextIndex] = result
                            val preloadDuration = System.currentTimeMillis() - preloadStartTime
                            Log.d("MainViewModel", "[Preload] Complete for index $nextIndex in ${preloadDuration}ms")
                        }
                    }

                    // 3. Wait the remaining time so the total cycle is exactly DISPLAY_DURATION
                    //    (preload window + remaining display time + crossfade)
                    delay(maxOf(0L, PRELOAD_TRIGGER_TIME - TRANSITION_DURATION))

                    // 4. Use the preloaded bitmap if available, otherwise load synchronously as fallback.
                    //    Skip undecodable images so one bad file cannot blank the screen.
                    var index = nextIndex
                    var attempts = 0
                    var result: BitmapResult? = null
                    while (attempts < currentState.images.size) {
                        val candidate = currentState.images[index]
                        result = preloadedBitmaps.remove(index) ?: run {
                            val syncLoadStartTime = System.currentTimeMillis()
                            Log.w("MainViewModel", "[Fallback] Preload not ready for index $index, loading synchronously")
                            val result = bitmapLoader.loadResizedBitmap(candidate.uri, DEFAULT_TARGET_WIDTH, DEFAULT_TARGET_HEIGHT)
                            Log.w("MainViewModel", "[Fallback] Synchronous load took ${System.currentTimeMillis() - syncLoadStartTime}ms")
                            result
                        }
                        if (result != null) {
                            break
                        }
                        Log.w("MainViewModel", "[Skip] Image at index $index failed to decode, trying the next one")
                        index = (index + 1) % currentState.images.size
                        attempts++
                    }

                    val loaded = result
                    // The bitmap being replaced. It is still on screen for the
                    // duration of the crossfade, so it cannot be recycled until
                    // the wait at step 6 completes.
                    val outgoing = _uiState.value.currentDisplayImage?.bitmap

                    if (loaded != null) {
                        val resolveStartTime = System.currentTimeMillis()
                        val metadataLabel = resolveLocationOrModel(loaded.metadata)
                        val resolveDuration = System.currentTimeMillis() - resolveStartTime
                        Log.d("MainViewModel", "[Metadata] Resolution took ${resolveDuration}ms for index $index")

                        // 5. Switch image (starts crossfade)
                        _uiState.update {
                            it.copy(
                                currentImageIndex = index,
                                currentDisplayImage = DisplayImage(loaded.bitmap, metadataLabel)
                            )
                        }
                    } else {
                        Log.w("MainViewModel", "[Skip] All images failed to decode, keeping the current image on screen")
                    }

                    // 6. Wait for image transition to complete
                    delay(TRANSITION_DURATION.toLong())

                    // 7. The crossfade is done and the outgoing bitmap is no
                    // longer on screen, so it can be released. Without this a
                    // slideshow keeps every full-screen bitmap it has shown
                    // until the GC gets around to collecting it.
                    if (outgoing != null && outgoing !== loaded?.bitmap) {
                        recycle(outgoing)
                    }
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
            // Emit one sample immediately so the overlay has data as soon as it appears
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

    /**
     * Releases a bitmap that is no longer referenced by any composable.
     *
     * Recycling is only safe once the bitmap has left the composition: Compose
     * keeps no reference to it, and the pixel memory is freed immediately rather
     * than waiting for a GC pass. Bitmap.recycle() is a no-op on a bitmap that is
     * already recycled, so double-release is harmless.
     */
    private fun recycle(bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    fun toggleOverlays() {
        val showingOverlays = !_uiState.value.areOverlaysVisible
        _uiState.update { it.copy(areOverlaysVisible = showingOverlays) }

        // Only poll memory usage while the overlay is visible
        if (showingOverlays) {
            startRamMonitoring()
        } else {
            stopRamMonitoring()
        }
    }

    override fun onCleared() {
        super.onCleared()

        // viewModelScope is cancelled by the framework, but cancelling explicitly
        // keeps every job's teardown in one place and stops a job that is mid-suspend
        // on a network read from holding its connection open any longer than needed.
        slideshowJob?.cancel()
        loadImagesJob?.cancel()
        preloadJob?.cancel()
        ramMonitoringJob?.cancel()

        // Nothing is on screen once the ViewModel is cleared, so every bitmap
        // still held here can be released outright.
        preloadedBitmaps.values.forEach { recycle(it.bitmap) }
        preloadedBitmaps.clear()
        _uiState.value.currentDisplayImage?.bitmap?.let { recycle(it) }
    }
}

data class LoadedImage(
    val uri: Uri,
    val metadataLabel: String?
)

data class DisplayImage(
    val bitmap: Bitmap,
    val metadataLabel: String?
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
    val areOverlaysVisible: Boolean = true,
    val transitionDuration: Int = 1000,
    val ramInfo: RamInfo? = null,
    val imageSource: String = "",
    val imageError: String? = null
)
