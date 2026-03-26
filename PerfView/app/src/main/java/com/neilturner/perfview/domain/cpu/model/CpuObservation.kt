package com.neilturner.perfview.domain.cpu.model

import com.neilturner.perfview.data.cpu.model.TopProcessUsage

data class CpuObservation(
    val percent: Float,
    val topProcesses: List<TopProcessUsage>,
    val collectedAtMillis: Long,
)
