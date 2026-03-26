package com.neilturner.perfview.data.cpu.model

object CpuReadException {
    fun fromError(error: CpuReadError): Exception =
        when (error) {
            is CpuReadError.AdbError -> Exception("ADB error: ${error.details}", error.cause)
            is CpuReadError.MissingCpuSummary -> Exception(error.message)
            is CpuReadError.MissingTotalCpuCapacity -> Exception(error.message)
            is CpuReadError.MissingIdleCpu -> Exception(error.message)
        }
}
