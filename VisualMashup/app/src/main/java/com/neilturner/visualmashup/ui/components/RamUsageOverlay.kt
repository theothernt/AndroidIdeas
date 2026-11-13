package com.neilturner.visualmashup.ui.components

import android.os.Debug
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

private const val UPDATE_INTERVAL_MS = 1_000L

/**
 * A small always-on overlay showing the app's current RAM usage.
 *
 * `totalPss` is the same proportional-set-size metric Android uses for a process, so it is the best
 * single "how much RAM is this app using" number. The Java heap figure is the live managed heap.
 */
@Composable
fun RamUsageOverlay(modifier: Modifier = Modifier) {
    var pssKb by remember { mutableIntStateOf(0) }
    var heapMb by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val memoryInfo = Debug.MemoryInfo()
        val runtime = Runtime.getRuntime()
        while (true) {
            Debug.getMemoryInfo(memoryInfo)
            pssKb = memoryInfo.totalPss
            heapMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)).toInt()
            delay(UPDATE_INTERVAL_MS)
        }
    }

    Text(
        text = "RAM %d MB  •  heap %d MB".format(pssKb / 1024, heapMb),
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}