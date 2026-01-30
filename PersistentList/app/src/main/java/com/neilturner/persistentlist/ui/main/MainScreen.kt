package com.neilturner.persistentlist.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
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
        // Left Column: Controls
        Column(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // Media Sources Button Style
            TvButton(
                text = "Load Files",
                icon = Icons.Default.Refresh,
                onClick = { viewModel.loadFiles() },
                modifier = Modifier.focusRequester(firstButtonFocusRequester)
            )

            Spacer(modifier = Modifier.height(12.dp))

            TvButton(
                text = "Clear DB",
                icon = Icons.Default.Delete,
                onClick = { viewModel.clearDb() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Scan Results
            if (isScanning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scanning...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            } else if (scanDuration != null) {
                Text(
                    text = "Last scan: $scanDuration ms",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                if (scanSource != null) {
                    Text(
                        text = "Source: $scanSource",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // File Count
            Text(
                text = "Files found: ${files.size}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Right Column: Results
        Column(
            modifier = Modifier
                .weight(0.7f)
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(files) { index, file ->
                        TvFileItem(
                            fileNumber = index + 1,
                            fileName = file,
                            onClick = { /* Handle click if needed */ }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvButton(
	text: String,
	icon: ImageVector,
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(8.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
	        containerColor = Color(0xFF1E1E1E),
	        focusedContainerColor = Color.White,
	        contentColor = Color.White,
	        focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1.0f
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvFileItem(
    fileNumber: Int,
    fileName: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(6.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E1E1E),
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1.0f
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$fileNumber. $fileName",
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}