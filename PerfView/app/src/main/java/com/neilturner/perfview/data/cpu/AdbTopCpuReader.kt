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
            .filter { it.cpuPercent > 0f || it.ramMb > 0f }
            .filterNot { shouldIgnoreProcess(it) }
            .take(DEFAULT_VISIBLE_LIMIT)

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
        // top output: PID USER PR NI VSZ RSS ? S %CPU %MEM TIME+ ARGS
        // groupValues: [0]=full, [1]=PID, [2]=USER, [3]=RSS, [4]=State, [5]=%CPU, [6]=%MEM, [7]=ARGS
        val rssValue = match.groupValues[3]
        val rssKb = parseMemoryValue(rssValue)
        return TopProcessUsage(
            pid = match.groupValues[1].toInt(),
            user = match.groupValues[2],
            state = match.groupValues[4],
            cpuPercent = match.groupValues[5].toFloat(),
            ramPercent = match.groupValues[6].toFloat(),
            ramMb = rssKb / 1024f,
            name = match.groupValues[7].trim(),
        )
    }

    private fun parseMemoryValue(value: String): Float {
        // Handle values like "2.7M", "75M", "2.2M", "34M", or plain numbers
        val numValue = value.removeSuffix("M").removeSuffix("G").toFloatOrNull() ?: 0f
        val multiplier = when {
            value.endsWith("G") -> 1024f * 1024f  // GB to KB
            value.endsWith("M") -> 1024f          // MB to KB
            else -> 1f                            // Already in KB
        }
        return numValue * multiplier
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
        // top output: PID USER PR NI VSZ RSS ? S %CPU %MEM TIME+ ARGS
        // Example: 3634 shell 20 0 10G 2.7M 2.2M S 0.0 0.1 0:00.09 logcat
        private val PROCESS_ROW = Regex(
            """^\s*(\d+)\s+(\S+)\s+\S+\s+\S+\s+\S+\s+(\S+)\s+\S+\s+(\S)\s+([0-9.]+)\s+([0-9.]+)\s+\S+\s+(.+)$"""
        )
    }
}
