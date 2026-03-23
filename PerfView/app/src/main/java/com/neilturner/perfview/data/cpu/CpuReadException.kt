package com.neilturner.perfview.data.cpu

/**
 * Exception thrown when CPU reading fails.
 * Wraps a [CpuReadError] for use with Kotlin's Result type.
 */
class CpuReadException(
    val error: CpuReadError
) : Exception(error.message, error.cause) {
    companion object {
        /**
         * Creates a CpuReadException from a CpuReadError.
         */
        fun fromError(error: CpuReadError): CpuReadException = CpuReadException(error)
    }
}
