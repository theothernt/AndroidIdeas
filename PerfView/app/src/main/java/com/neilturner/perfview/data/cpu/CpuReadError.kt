package com.neilturner.perfview.data.cpu

/**
 * Represents errors that can occur when reading CPU data from ADB.
 */
sealed class CpuReadError(
    open val message: String,
    open val cause: Throwable? = null
) {
    /**
     * The top command output did not include a CPU summary line.
     */
    data object MissingCpuSummary : CpuReadError(
        message = "top output did not include a CPU summary"
    )

    /**
     * The top output did not expose total CPU capacity.
     */
    data object MissingTotalCpuCapacity : CpuReadError(
        message = "top output did not expose total CPU capacity"
    )

    /**
     * The top output did not expose idle CPU percentage.
     */
    data object MissingIdleCpu : CpuReadError(
        message = "top output did not expose idle CPU"
    )

    /**
     * The ADB shell command produced no readable output.
     */
    data class NoOutput(
        val command: String
    ) : CpuReadError(message = "ADB command returned no output: $command")

    /**
     * ADB connection is unavailable or the shell command failed.
     */
    data class AdbError(
        override val message: String,
        override val cause: Throwable?
    ) : CpuReadError(message, cause)

    /**
     * An unexpected error occurred during CPU reading.
     */
    data class Unknown(
        override val message: String,
        override val cause: Throwable?
    ) : CpuReadError(message, cause)

    /**
     * Converts this error to a user-friendly message.
     */
    fun toUserMessage(): String = when (this) {
        is MissingCpuSummary -> "Unable to read CPU data from the device"
        is MissingTotalCpuCapacity -> "Device CPU data format not recognized"
        is MissingIdleCpu -> "Device CPU data format not recognized"
        is NoOutput -> "ADB command produced no output"
        is AdbError -> message
        is Unknown -> "An unexpected error occurred: $message"
    }
}
