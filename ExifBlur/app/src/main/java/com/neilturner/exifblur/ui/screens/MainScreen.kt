package com.neilturner.exifblur.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel

import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.SurfaceDefaults

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import com.neilturner.exifblur.ui.components.Slideshow

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
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
                Slideshow(
                    currentBitmap = uiState.currentDisplayImage?.bitmap,
                    transitionDuration = uiState.transitionDuration
                )
            }

            if (uiState.isPermissionCheckComplete) {
                if (!uiState.hasPermission) {
                    Button(
                        onClick = { launcher.launch(permissions.toTypedArray()) },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .focusRequester(focusRequester)
                    ) {
                        Text(text = "Ask for permission to access images")
                    }
                } else if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading images and metadata...",
                            color = Color.White
                        )
                    }
                } else {
                    // Bottom Left: Image Count
                    AnimatedVisibility(
                        visible = uiState.areOverlaysVisible,
                        enter = fadeIn(animationSpec = tween(durationMillis = 1000)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 1000)),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .alpha(0.8F)
                                .background(
                                    color = Color.Black,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            val statusText = if (uiState.imageCount == 0) {
                                "No images found"
                            } else {
                                "Image ${uiState.currentImageIndex + 1} of ${uiState.imageCount}"
                            }
                            Text(
                                text = statusText,
                                color = Color.White
                            )
                        }
                    }

                    // Bottom Right: Location or Camera Model
                    uiState.currentDisplayImage?.metadataLabel?.let { label ->
                        AnimatedVisibility(
                            visible = uiState.areOverlaysVisible,
                            enter = fadeIn(animationSpec = tween(durationMillis = 1000)),
                            exit = fadeOut(animationSpec = tween(durationMillis = 1000)),
                            modifier = Modifier.align(Alignment.BottomEnd)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .alpha(0.8F)
                                    .background(
                                        color = Color.Black,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Top Right: RAM Usage
                    uiState.ramInfo?.let { ram ->
                        AnimatedVisibility(
                            visible = true, // Always visible
                            enter = fadeIn(animationSpec = tween(durationMillis = 500)),
                            exit = fadeOut(animationSpec = tween(durationMillis = 500)),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .alpha(0.8F)
                                    .background(
                                        color = Color.Black,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "RAM: ${ram.usedMemoryMB}MB/${ram.maxMemoryMB}MB",
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Usage: ${ram.usagePercentage}%",
                                        color = if (ram.usagePercentage > 80) Color.Red else Color.White
                                    )
                                    Text(
                                        text = "Native: ${ram.nativeHeapMB}MB",
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
