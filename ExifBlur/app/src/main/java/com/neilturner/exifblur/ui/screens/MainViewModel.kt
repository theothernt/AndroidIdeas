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

    companion object {
        const val TRANSITION_DURATION = 1500 // Image crossfade duration (ms)
        const val OVERLAY_FADE_DURATION = 500L // Text fade duration (ms)
        const val DISPLAY_DURATION = 4000L // How long to show image before transitioning
        const val RAM_UPDATE_INTERVAL = 1000L // Update RAM usage every second
    }

    fun updatePermissionStatus(granted: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasPermission = granted,
            isPermissionCheckComplete = true,
            transitionDuration = TRANSITION_DURATION,
            overlayFadeDuration = OVERLAY_FADE_DURATION.toInt()
        )
        if (granted) {
            loadImages()
            startRamMonitoring()
        }
    }

    private fun loadImages() {
        viewModelScope.launch {
            Log.d("MainViewModel", "Starting image loading process...")
            val startTime = System.currentTimeMillis()
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val fetchUrisStartTime = System.currentTimeMillis()
            val uris = imageRepository.getImages()
            val fetchUrisDuration = System.currentTimeMillis() - fetchUrisStartTime
            Log.d("MainViewModel", "Fetched ${uris.size} image URIs in ${fetchUrisDuration}ms")
            
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
                bitmapHelper.loadResizedBitmap(loadedImages[0].uri)
            } else null
            
            val initialMetadataLabel = initialResult?.metadata?.let { resolveLocationOrModel(it) }

            val bitmapDuration = System.currentTimeMillis() - bitmapStartTime
            if (initialResult != null) {
                Log.d("MainViewModel", "Loaded initial bitmap with metadata in ${bitmapDuration}ms")
            }

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d("MainViewModel", "Image loading process complete. Total images: ${loadedImages.size}, Total time: ${totalDuration}ms")

            _uiState.value = _uiState.value.copy(
                imageCount = loadedImages.size,
                images = loadedImages,
                isLoading = false,
                currentImageIndex = 0,
                currentDisplayImage = initialResult?.let { DisplayImage(it.bitmap, initialMetadataLabel) },
                areOverlaysVisible = true
            )
            
            if (loadedImages.isNotEmpty()) {
                startSlideshow()
            }
        }
    }

    private fun startSlideshow() {
        viewModelScope.launch {
            while (isActive) {
                // 1. Wait while displaying the current image
                delay(DISPLAY_DURATION)

                val currentState = _uiState.value
                if (currentState.images.isNotEmpty()) {
                    // 2. Fade out overlays
                    _uiState.update { it.copy(areOverlaysVisible = false) }
                    delay(OVERLAY_FADE_DURATION)

                    // 3. Prepare next image
                    val nextIndex = (currentState.currentImageIndex + 1) % currentState.images.size
                    val nextImage = currentState.images[nextIndex]
                    
                    // Load the next bitmap (this will also load EXIF data)
                    val result = bitmapHelper.loadResizedBitmap(nextImage.uri)
                    
                    val metadataLabel = result?.metadata?.let { resolveLocationOrModel(it) }
                    
                    // 4. Switch image (starts crossfade)
                    _uiState.update { 
                        it.copy(
                            currentImageIndex = nextIndex,
                            currentDisplayImage = result?.let { res -> DisplayImage(res.bitmap, metadataLabel) }
                        ) 
                    }
                    
                    // 5. Wait for image transition to mostly complete before showing new text
                    delay(TRANSITION_DURATION - OVERLAY_FADE_DURATION)

                    // 6. Fade in overlays
                    _uiState.update { it.copy(areOverlaysVisible = true) }
                }
            }
        }
    }

    private suspend fun resolveLocationOrModel(exif: ExifMetadata?): String? {
        if (exif == null) return null
        
        val address = if (exif.latitude != null && exif.longitude != null) {
            locationHelper.getAddressFromLocation(exif.latitude, exif.longitude)
        } else null

        if (!imageRepository.isExifEnabled()) {
            return address
        }

        if (address != null) return address

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
        exif.cameraModel?.let { parts.add(it) }
        exif.focalLength?.let { 
            val fl = it.toDoubleOrNull()
            if (fl != null) parts.add("${fl.toInt()}mm") else parts.add(it)
        }
        exif.aperture?.let { parts.add("f/$it") }
        exif.shutterSpeed?.let { 
            val ss = it.toDoubleOrNull()
            if (ss != null) {
                if (ss < 1.0) {
                    val reciprocal = (1.0 / ss).toInt()
                    parts.add("1/$reciprocal s")
                } else {
                    parts.add("${ss}s")
                }
            } else {
                parts.add("${it}s")
            }
        }
        exif.iso?.let { parts.add("ISO $it") }

        return if (parts.isNotEmpty()) parts.joinToString(" • ") else null
    }

    private fun startRamMonitoring() {
        viewModelScope.launch {
            while (isActive) {
                val ramInfo = ramMonitor.getCurrentRamUsage()
                _uiState.update { it.copy(ramInfo = ramInfo) }
                delay(RAM_UPDATE_INTERVAL)
            }
        }
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
    val overlayFadeDuration: Int = 500,
    val ramInfo: RamInfo? = null
)
