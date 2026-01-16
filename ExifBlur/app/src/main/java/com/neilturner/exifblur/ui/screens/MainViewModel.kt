package com.neilturner.exifblur.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.exifblur.data.ExifMetadata
import com.neilturner.exifblur.data.ImageRepository
import com.neilturner.exifblur.util.BitmapHelper
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

    companion object {
        const val TRANSITION_DURATION = 2000 // Image crossfade duration (ms)
        const val DISPLAY_DURATION = 6000L // How long to show image before transitioning
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
                areOverlaysVisible = false
            )
            
            if (loadedImages.isNotEmpty()) {
                startSlideshow()
            }
        }
    }

    private fun startSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            while (isActive) {
                // 1. Wait while displaying the current image
                delay(DISPLAY_DURATION)

                val currentState = _uiState.value
                if (currentState.images.isNotEmpty()) {
                    // 2. Prepare next image
                    val nextIndex = (currentState.currentImageIndex + 1) % currentState.images.size
                    val nextImage = currentState.images[nextIndex]
                    
                    // Load the next bitmap (this will also load EXIF data)
                    val result = bitmapHelper.loadResizedBitmap(nextImage.uri)
                    
                    val resolveStartTime = System.currentTimeMillis()
                    val metadataLabel = result?.metadata?.let { resolveLocationOrModel(it) }
                    // Log.d("MainViewModel", "Metadata resolution took ${System.currentTimeMillis() - resolveStartTime}ms for index $nextIndex")
                    
                    // 3. Switch image (starts crossfade)
                    _uiState.update { 
                        it.copy(
                            currentImageIndex = nextIndex,
                            currentDisplayImage = result?.let { res -> DisplayImage(res.bitmap, metadataLabel, res.rotation) }
                        ) 
                    }
                    
                    // 4. Wait for image transition to complete
                    delay(TRANSITION_DURATION.toLong())
                }
            }
        }
    }

    private suspend fun resolveLocationOrModel(exif: ExifMetadata?): String? {
        if (exif == null) return null
        
        val resolveStartTime = System.currentTimeMillis()
        var locationLabel = if (exif.latitude != null && exif.longitude != null) {
            val address = locationHelper.getAddressFromLocation(exif.latitude, exif.longitude)
            val coords = "(${String.format("%.4f", exif.latitude)}, ${String.format("%.4f", exif.longitude)})"
            if (address != null) "$address $coords" else coords
        } else null
        
        if (locationLabel != null) {
            Log.d("MainViewModel", "Reverse geocoding took ${System.currentTimeMillis() - resolveStartTime}ms")
        }

        if (!imageRepository.isExifEnabled()) {
            return locationLabel
        }

        if (locationLabel != null) return locationLabel

        val parts = mutableListOf<String>()
        exif.date?.let { rawDate ->
            // EXIF date is usually "YYYY:MM:DD HH:MM:SS"
            val formattedDate = try {
                val components = rawDate.split(" ")[0].split(":")
                if (components.size == 3) {
                    val year = components[0]
                    val month = components[1].toInt()
                    val day = components[2].toInt()
                    val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    "$day ${monthNames[month - 1]} $year"
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
    val ramInfo: RamInfo? = null
)
