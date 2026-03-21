package com.neilturner.perfview.data.cpu

interface CpuRepository {
    suspend fun readSnapshot(): CpuUsageSnapshot
}
