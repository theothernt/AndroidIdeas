package com.neilturner.perfview.data.cpu

import android.util.Log
import com.neilturner.perfview.data.adb.AdbShellClient
import com.neilturner.perfview.data.adb.AdbShellException

class AdbTopCpuReader(
    private val adbShellClient: AdbShellClient,
) {
    /**
     * Reads a snapshot of CPU usage data.
     *
     * @param limit Maximum number of processes to include in the output
     * @return Result containing the CPU snapshot or an error
     */
    suspend fun readSnapshot(limit: Int = COMMAND_LIMIT): Result<CpuUsageSnapshot> {
        val command = "top -n 1 -b -m $limit"
        Log.d(TAG, "Running process snapshot command: $command")

        val output = try {
            adbShellClient.run(command)
        } catch (exception: AdbShellException) {
            Log.e(TAG, "ADB shell command failed", exception)
            return Result.failure(CpuReadException.fromError(
                CpuReadError.AdbError(exception.message ?: "Unknown ADB error", exception)
            ))
        } catch (exception: Exception) {
            Log.e(TAG, "Unexpected error during ADB shell command", exception)
            return Result.failure(CpuReadException.fromError(
                CpuReadError.AdbError(exception.message ?: "Unknown error", exception)
            ))
        }

        val lines = output.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()
        Log.d(TAG, "Received ${lines.size} non-blank lines from top")

        val totalLine = lines.firstOrNull { it.contains("%cpu") }
            ?: return Result.failure(CpuReadException.fromError(CpuReadError.MissingCpuSummary))

        val totalCpuPercentResult = parseTotalCpuPercent(totalLine)
        val totalCpuPercent = totalCpuPercentResult.getOrElse { error ->
            return Result.failure(error)
        }

        val processRows = lines
            .dropWhile { !it.contains("ARGS") }
            .drop(1)
            .mapNotNull { line -> parseProcessRow(line) }
            .filter { it.cpuPercent > 0f }
            .filterNot { shouldIgnoreProcess(it) }
            .take(DEFAULT_VISIBLE_LIMIT)
        Log.d(TAG, "Parsed ${processRows.size} visible process rows")

        return Result.success(
            CpuUsageSnapshot(
                totalCpuPercent = totalCpuPercent,
                topProcesses = processRows,
                timestampMillis = System.currentTimeMillis(),
            )
        )
    }

    private fun parseTotalCpuPercent(line: String): Result<Float> {
        val totalCapacity = PERCENT_TOKEN.find(line)?.groupValues?.get(1)?.toFloatOrNull()
            ?: return Result.failure(CpuReadException.fromError(CpuReadError.MissingTotalCpuCapacity))

        val idlePercent = IDLE_TOKEN.find(line)?.groupValues?.get(1)?.toFloatOrNull()
            ?: return Result.failure(CpuReadException.fromError(CpuReadError.MissingIdleCpu))

        val cpuPercent = ((totalCapacity - idlePercent) / totalCapacity * 100f).coerceIn(0f, 100f)
        return Result.success(cpuPercent)
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
