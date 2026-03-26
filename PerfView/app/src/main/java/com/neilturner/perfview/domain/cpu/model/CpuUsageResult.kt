package com.neilturner.perfview.domain.cpu.model

import com.neilturner.perfview.data.cpu.model.TopProcessUsage

sealed interface CpuUsageResult {
    data class Success(
        val observation: CpuObservation,
    ) : CpuUsageResult

    data class Unsupported(
        val message: String,
    ) : CpuUsageResult
}
