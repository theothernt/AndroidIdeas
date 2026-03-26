package com.neilturner.perfview.data.cpu.model

sealed class CpuReadError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    data class AdbError(
        val details: String,
        override val cause: Throwable?,
    ) : CpuReadError(details, cause)

    data object MissingCpuSummary : CpuReadError(
        "ADB output did not contain a CPU summary line. The 'top' command format may have changed.",
    )

    data object MissingTotalCpuCapacity : CpuReadError(
        "Could not parse total CPU capacity from ADB output.",
    )

    data object MissingIdleCpu : CpuReadError(
        "Could not parse idle CPU percentage from ADB output.",
    )
}
