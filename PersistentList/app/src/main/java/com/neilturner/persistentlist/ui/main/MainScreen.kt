package com.neilturner.persistentlist.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Left Column: Controls
        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { viewModel.scanFiles() }) {
                Text("Scan")
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState) {
                is MainUiState.Loading -> {
                    CircularProgressIndicator(color = Color.White)
                }
                is MainUiState.Success -> {
                    Text(
                        text = "Time: ${state.scanDurationMillis} ms",
                        color = Color.White
                    )
                }
                else -> {
                    // Placeholder to keep layout stable or empty
                }
            }
        }

        // Right Column: Results
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                is MainUiState.Success -> {
                    Text(
                        "Found ${state.files.size} files",
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.files) { file ->
                            Text(
                                text = file.toString(),
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
                is MainUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                         Text("Error: ${state.message}", color = Color.Red)
                    }
                }
                is MainUiState.Initial -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results yet", color = Color.Gray)
                    }
                }
                is MainUiState.Loading -> {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Optional: Text("Loading results...")
                    }
                }
            }
        }
    }
}
