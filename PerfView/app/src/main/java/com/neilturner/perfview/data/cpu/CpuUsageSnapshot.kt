package com.neilturner.perfview.data.cpu

data class CpuUsageSnapshot(
    val totalCpuPercent: Float,
    val topProcesses: List<TopProcessUsage>,
    val timestampMillis: Long,
)
