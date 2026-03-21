package com.neilturner.perfview.data.cpu

class CpuRepositoryImpl(
    private val adbTopCpuReader: AdbTopCpuReader,
) : CpuRepository {
    override suspend fun readSnapshot(): CpuUsageSnapshot = adbTopCpuReader.readSnapshot()
}
