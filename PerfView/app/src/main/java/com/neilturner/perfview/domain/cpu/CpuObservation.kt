package com.neilturner.perfview.domain.cpu

import com.neilturner.perfview.data.cpu.CpuDataSource
import com.neilturner.perfview.data.cpu.TopProcessUsage

data class CpuObservation(
    val percent: Float,
    val topProcesses: List<TopProcessUsage>,
    val collectedAtMillis: Long,
    val dataSource: CpuDataSource,
)
