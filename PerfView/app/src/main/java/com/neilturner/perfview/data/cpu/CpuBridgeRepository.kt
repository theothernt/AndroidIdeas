package com.neilturner.perfview.data.cpu

interface CpuBridgeRepository {
    suspend fun readCpuData(): BridgeCpuData
}
