package com.neilturner.exifblur.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import kotlin.math.roundToInt

data class RamInfo(
    val usedMemoryMB: Int,
    val maxMemoryMB: Int,
    val usagePercentage: Int,
    val nativeHeapMB: Int
)

class RamMonitor(private val context: Context) {
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    fun getCurrentRamUsage(): RamInfo {
        // Get memory info for the app
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        // Get app's memory usage
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        
        // Get native heap usage
        val nativeHeapSize = Debug.getNativeHeapAllocatedSize()
        
        return RamInfo(
            usedMemoryMB = (usedMemory / 1024 / 1024).toInt(),
            maxMemoryMB = (maxMemory / 1024 / 1024).toInt(),
            usagePercentage = ((usedMemory.toDouble() / maxMemory.toDouble()) * 100).roundToInt(),
            nativeHeapMB = (nativeHeapSize / 1024 / 1024).toInt()
        )
    }
    
    fun getFormattedRamUsage(): String {
        val ramInfo = getCurrentRamUsage()
        return "${ramInfo.usedMemoryMB}MB/${ramInfo.maxMemoryMB}MB (${ramInfo.usagePercentage}%)"
    }
}
