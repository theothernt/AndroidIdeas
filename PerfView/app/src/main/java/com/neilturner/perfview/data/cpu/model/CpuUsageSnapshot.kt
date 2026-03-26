package com.neilturner.perfview.data.cpu.model

data class CpuUsageSnapshot(
    val totalCpuPercent: Float,
    val topProcesses: List<TopProcessUsage>,
    val timestampMillis: Long,
)
