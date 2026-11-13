package com.neilturner.visualmashup.ui.components

import android.os.Debug
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

private const val UPDATE_INTERVAL_MS = 1_000L

/**
 * A small always-on overlay showing the app's current RAM usage.
 *
 * `totalPss` is the same proportional-set-size metric Android uses for a process, so it is the best
 * single "how much RAM is this app using" number.
 *
 * `Debug.getMemoryInfo` walks `/proc/self/smaps` and takes ~100 ms on TV hardware, so it runs on
 * [Dispatchers.Default] and only the result is published to a [MutableStateFlow]. Polling it on the
 * UI thread used to stall every animated screen once a second, which contaminated perf runs.
 */
@Composable
fun RamUsageOverlay(modifier: Modifier = Modifier) {
    val pssKb = remember { MutableStateFlow(0) }
    val pss by pssKb.collectAsStateWithLifecycle()

    LaunchedEffect(pssKb) {
        while (true) {
            val totalPss = withContext(Dispatchers.Default) {
                val memoryInfo = Debug.MemoryInfo()
                Debug.getMemoryInfo(memoryInfo)
                memoryInfo.totalPss
            }
            pssKb.value = totalPss
            delay(UPDATE_INTERVAL_MS)
        }
    }

    OverlayChip(
        text = "RAM usage %d MB".format(pss / 1024),
        modifier = modifier,
    )
}
