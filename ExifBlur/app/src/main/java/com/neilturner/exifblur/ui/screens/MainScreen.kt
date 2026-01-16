package com.neilturner.exifblur.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.neilturner.exifblur.ui.components.ImageCountOverlay
import com.neilturner.exifblur.ui.components.LoadingContent
import com.neilturner.exifblur.ui.components.MetadataOverlay
import com.neilturner.exifblur.ui.components.PermissionRequestContent
import com.neilturner.exifblur.ui.components.RamUsageOverlay
import com.neilturner.exifblur.ui.components.Slideshow
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val permissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val isGranted = permissionsMap.values.all { it }
        viewModel.updatePermissionStatus(isGranted)
    }

    LaunchedEffect(Unit) {
        val isGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        viewModel.updatePermissionStatus(isGranted)
    }

    LaunchedEffect(uiState.hasPermission, uiState.isPermissionCheckComplete) {
        if (uiState.isPermissionCheckComplete && !uiState.hasPermission) {
            focusRequester.requestFocus()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RectangleShape,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.currentDisplayImage != null) {
                val displayImage = uiState.currentDisplayImage!!
                Slideshow(
                    currentImage = com.neilturner.exifblur.ui.components.SlideshowData(
                        bitmap = displayImage.bitmap,
                        rotation = displayImage.rotation
                    ),
                    transitionDuration = uiState.transitionDuration
                )
            }

            if (uiState.isPermissionCheckComplete) {
                if (!uiState.hasPermission) {
                    PermissionRequestContent(
                        onRequestPermission = { launcher.launch(permissions.toTypedArray()) },
                        focusRequester = focusRequester,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (uiState.isLoading) {
                    LoadingContent()
                } else {
                    // Overlays
                    ImageCountOverlay(
                        isVisible = uiState.areOverlaysVisible,
                        currentImageIndex = uiState.currentImageIndex,
                        imageCount = uiState.imageCount,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )

                    uiState.currentDisplayImage?.metadataLabel?.let { label ->
                        MetadataOverlay(
                            isVisible = uiState.areOverlaysVisible,
                            label = label,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }

                    uiState.ramInfo?.let { ram ->
                        RamUsageOverlay(
                            ramInfo = ram,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }
            }
        }
    }
}
