package com.neilturner.persistentlist.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = koinViewModel()) {
    val files by viewModel.files.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanDuration by viewModel.scanDurationMillis.collectAsState()
    val scanSource by viewModel.scanSource.collectAsState()
    val error by viewModel.error.collectAsState()
    val hasLoadedFiles by viewModel.hasLoadedFiles.collectAsState()
    
    val highlightedIndex by viewModel.highlightedIndex.collectAsState()
    val isHighlighting by viewModel.isHighlighting.collectAsState()
    
    val firstButtonFocusRequester = remember { FocusRequester() }
    
    // Request focus on first button when screen loads
    LaunchedEffect(Unit) {
        firstButtonFocusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        ControlsSection(
            onLoadFromSamba = { viewModel.loadFromSamba() },
            onLoadFromDb = { viewModel.loadFromDb() },
            onClearDb = { viewModel.clearDb() },
            onStartHighlighting = { viewModel.startHighlighting() },
            onStopHighlighting = { viewModel.stopHighlighting() },
            isScanning = isScanning,
            isHighlighting = isHighlighting,
            scanDuration = scanDuration,
            scanSource = scanSource,
            fileCount = files.size,
            focusRequester = firstButtonFocusRequester,
            modifier = Modifier.weight(0.3f)
        )

        ResultsSection(
            error = error,
            files = files,
            hasLoadedFiles = hasLoadedFiles,
            isScanning = isScanning,
            highlightedIndex = highlightedIndex,
            modifier = Modifier.weight(0.7f)
        )
    }
}
