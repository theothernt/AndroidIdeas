package com.neilturner.perfview.data.cpu

import android.util.Log
import com.neilturner.perfview.data.adb.AdbShellClient

class AdbTopCpuReader(
    private val adbShellClient: AdbShellClient,
) {
    suspend fun readSnapshot(limit: Int = COMMAND_LIMIT): CpuUsageSnapshot {
        val command = "top -n 1 -b -m $limit"
        Log.d(TAG, "Running process snapshot command: $command")
        val output = adbShellClient.run(command)
        val lines = output.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()
        Log.d(TAG, "Received ${lines.size} non-blank lines from top")

        val totalLine = lines.firstOrNull { it.contains("%cpu") }
            ?: error("top output did not include a CPU summary")
        val totalCpuPercent = parseTotalCpuPercent(totalLine)

        val processRows = lines
            .dropWhile { !it.contains("ARGS") }
            .drop(1)
            .mapNotNull(::parseProcessRow)
            .filter { it.cpuPercent > 0f }
            .filterNot(::shouldIgnoreProcess)
            .take(DEFAULT_VISIBLE_LIMIT)
        Log.d(TAG, "Parsed ${processRows.size} visible process rows")

        return CpuUsageSnapshot(
            totalCpuPercent = totalCpuPercent,
            topProcesses = processRows,
            timestampMillis = System.currentTimeMillis(),
        )
    }

    private fun parseTotalCpuPercent(line: String): Float {
        val totalCapacity = PERCENT_TOKEN.find(line)?.groupValues?.get(1)?.toFloatOrNull()
            ?: error("top output did not expose total CPU capacity")
        val idlePercent = IDLE_TOKEN.find(line)?.groupValues?.get(1)?.toFloatOrNull()
            ?: error("top output did not expose idle CPU")
        return ((totalCapacity - idlePercent) / totalCapacity * 100f).coerceIn(0f, 100f)
    }

    private fun parseProcessRow(line: String): TopProcessUsage? {
        val match = PROCESS_ROW.find(line) ?: return null
        return TopProcessUsage(
            pid = match.groupValues[1].toInt(),
            user = match.groupValues[2],
            state = match.groupValues[3],
            cpuPercent = match.groupValues[4].toFloat(),
            name = match.groupValues[5].trim(),
        )
    }

    private fun shouldIgnoreProcess(process: TopProcessUsage): Boolean {
        val name = process.name.lowercase()
        return IGNORED_PROCESS_NAMES.any { ignored ->
            name == ignored || name.startsWith("$ignored ")
        }
    }

    private companion object {
        private const val TAG = "PerfViewTop"
        private const val COMMAND_LIMIT = 20
        private const val DEFAULT_VISIBLE_LIMIT = 10
        private val IGNORED_PROCESS_NAMES = setOf(
            "top",
            "adbd",
            "sh",
            "logcat",
            "process-tracker",
        )
        private val PERCENT_TOKEN = Regex("""^\s*([0-9.]+)%cpu""")
        private val IDLE_TOKEN = Regex("""([0-9.]+)%idle""")
        private val PROCESS_ROW = Regex(
            """^\s*(\d+)\s+(\S+)\s+\S+\s+\S+\s+\S+\s+\S+\s+\S+\s+(\S)\s+([0-9.]+)\s+\S+\s+\S+\s+(.+)$"""
        )
    }
}
