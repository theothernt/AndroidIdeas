package com.neilturner.perfview.overlay

import com.neilturner.perfview.data.cpu.model.CpuUsageSnapshot
import com.neilturner.perfview.data.cpu.model.TopProcessUsage
import com.neilturner.perfview.domain.cpu.repository.CpuRepository

/**
 * Stands in for the ADB-backed repository so instrumented tests never open a socket to
 * 127.0.0.1:5555.
 *
 * On a fresh install the app's ADB key is unknown to adbd, so the first poll raises the
 * system "Allow USB debugging?" dialog. That is a system UI window which takes focus,
 * and it reliably breaks any Compose UI test running later in the same process. Whether
 * the dialog appears is not something a test can control, and it cannot be pre-approved
 * on a non-rooted Play emulator image, so the tests avoid dialling ADB at all.
 */
class FakeCpuRepository : CpuRepository {
    override suspend fun readSnapshot(): CpuUsageSnapshot = CpuUsageSnapshot(
        totalCpuPercent = 12.5f,
        topProcesses = listOf(
            TopProcessUsage(
                pid = 1,
                name = "com.example.fake",
                cpuPercent = 12.5f,
                ramPercent = 0.5f,
                ramMb = 64f,
                user = "u0_a101",
                state = "R",
            ),
        ),
        timestampMillis = FIXED_TIMESTAMP_MILLIS,
    )

    private companion object {
        const val FIXED_TIMESTAMP_MILLIS = 1_700_000_000_000L
    }
}
