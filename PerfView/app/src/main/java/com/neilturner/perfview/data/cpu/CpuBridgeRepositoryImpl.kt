package com.neilturner.perfview.data.cpu

class CpuBridgeRepositoryImpl(
    private val cpuBridgeClient: CpuBridgeClient,
) : CpuBridgeRepository {
    override suspend fun readCpuData(): BridgeCpuData = BridgeCpuData(
        percent = cpuBridgeClient.fetchCpuPercent(),
        timestampMillis = System.currentTimeMillis(),
    )
}
