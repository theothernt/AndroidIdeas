package com.neilturner.perfview.data.cpu.repository

import com.neilturner.perfview.data.cpu.model.CpuUsageSnapshot
import com.neilturner.perfview.data.cpu.source.AdbTopCpuReader
import com.neilturner.perfview.domain.cpu.repository.CpuRepository

class CpuRepositoryImpl(
    private val adbTopCpuReader: AdbTopCpuReader,
) : CpuRepository {
    override suspend fun readSnapshot(): CpuUsageSnapshot {
        return adbTopCpuReader.readSnapshot().getOrThrow()
    }
}
