package com.neilturner.perfview.domain.cpu.repository

import com.neilturner.perfview.data.cpu.model.CpuUsageSnapshot

interface CpuRepository {
    suspend fun readSnapshot(): CpuUsageSnapshot
}
