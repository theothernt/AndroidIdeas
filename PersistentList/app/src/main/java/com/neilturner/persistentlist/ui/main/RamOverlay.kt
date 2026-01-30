package com.neilturner.persistentlist.ui.main

import android.os.Debug
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RamOverlay(modifier: Modifier = Modifier) {
    var usedMem by remember { mutableStateOf(0L) }
    var freeMem by remember { mutableStateOf(0L) }
    var totalMem by remember { mutableStateOf(0L) }
    var maxMem by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            val runtime = Runtime.getRuntime()
            totalMem = runtime.totalMemory()
            freeMem = runtime.freeMemory()
            maxMem = runtime.maxMemory()
            usedMem = totalMem - freeMem
            delay(1000) // Update every second
        }
    }

    Box(
        modifier = modifier
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column {
            Text(
                text = "RAM Usage",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Used: ${usedMem / 1024 / 1024} MB",
                color = Color.Green,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Total: ${totalMem / 1024 / 1024} MB",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Max: ${maxMem / 1024 / 1024} MB",
                color = Color.Gray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
