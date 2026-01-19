package com.neilturner.overlayparty.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.neilturner.overlayparty.R
import kotlinx.coroutines.delay
import java.lang.reflect.Field

@Composable
fun Slideshow(
    modifier: Modifier = Modifier,
    delayMillis: Long = 12000L,
    crossfadeDurationMillis: Int = 2000
) {
    val context = LocalContext.current
    
    // Get all raw resource IDs dynamically
    val imageIds = remember {
        try {
            val fields: Array<Field> = R.raw::class.java.fields
            fields.map { it.getInt(null) }
        } catch (e: Exception) {
            emptyList<Int>()
        }
    }

    if (imageIds.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(delayMillis)
            currentIndex = (currentIndex + 1) % imageIds.size
        }
    }

    Crossfade(
        targetState = imageIds[currentIndex],
        animationSpec = tween(durationMillis = crossfadeDurationMillis),
        modifier = modifier.fillMaxSize()
    ) { imageId ->
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageId)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}