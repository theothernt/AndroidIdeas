package com.neilturner.overlayparty.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen(viewModel: MainViewModel = koinViewModel()) {
    val topLeftText by viewModel.topLeftText.collectAsState()
    val topRightText by viewModel.topRightText.collectAsState()
    val bottomLeftText by viewModel.bottomLeftText.collectAsState()
    val bottomRightText by viewModel.bottomRightText.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        TextBlock(
            text = topLeftText,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
        TextBlock(
            text = topRightText,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )
        TextBlock(
            text = bottomLeftText,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
        TextBlock(
            text = bottomRightText,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
fun TextBlock(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}
