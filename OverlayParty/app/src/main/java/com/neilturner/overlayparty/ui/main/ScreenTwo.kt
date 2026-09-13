package com.neilturner.overlayparty.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

private const val SLIDESHOW_IMAGE_PATH = "file:///android_asset/slideshow/"

@Composable
fun ScreenTwo(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(context)
                    .data(SLIDESHOW_IMAGE_PATH + "tomo_m_wegl4zgnhpe_unsplash.jpg")
                    .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
