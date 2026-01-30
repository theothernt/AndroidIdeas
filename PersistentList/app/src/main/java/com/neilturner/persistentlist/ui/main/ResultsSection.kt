package com.neilturner.persistentlist.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ResultsSection(
    error: String?,
    files: List<String>,
    hasLoadedFiles: Boolean,
    isScanning: Boolean,
    highlightedIndex: Int?,
    viewedCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        if (error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $error", color = Color.Red)
            }
        } else if (files.isEmpty() || !hasLoadedFiles) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (isScanning) "Scanning..." else "No files found", color = Color.Gray)
            }
        } else {
            val listState = rememberLazyListState()
            
            // Auto-scroll to highlighted item
            if (highlightedIndex != null) {
                androidx.compose.runtime.LaunchedEffect(highlightedIndex) {
                    listState.animateScrollToItem(highlightedIndex)
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(files, key = { _, file -> file }) { index, file ->
                    TvFileItem(
                        fileNumber = index + 1 + viewedCount,
                        fileName = file,
                        isHighlighted = index == highlightedIndex,
                        onClick = { /* Handle click if needed */ }
                    )
                }
            }
        }
    }
}
