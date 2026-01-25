package com.neilturner.fadeloop.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MemoryMonitor(modifier: Modifier = Modifier) {
    var memoryText by remember { mutableStateOf("Mem: ...") }

    LaunchedEffect(Unit) {
        while (true) {
            val runtime = Runtime.getRuntime()
            val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
            val maxMemInMB = runtime.maxMemory() / 1048576L
            val availMemInMB = runtime.freeMemory() / 1048576L
            
            memoryText = "Used: ${usedMemInMB}MB / Max: ${maxMemInMB}MB"
            delay(1000)
        }
    }

    Text(
        text = memoryText,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.technicalBackground()
    )
}
