package com.neilturner.perfview.data.adb

/**
 * Client for executing shell commands via ADB.
 */
fun interface AdbShellClient {
    /**
     * Executes a shell command and returns the output.
     *
     * @param command The shell command to execute
     * @return The command output as a string
     * @throws AdbShellException if the command fails or returns no output
     */
    @Throws(AdbShellException::class)
    suspend fun run(command: String): String
}

/**
 * Exception thrown when an ADB shell command fails.
 */
open class AdbShellException(
    message: String,
    val command: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * No output was produced by the command.
     */
    class NoOutputException(command: String) : AdbShellException(
        message = "ADB command produced no output: $command",
        command = command
    )

    /**
     * The ADB connection failed.
     */
    class ConnectionException(
        command: String,
        cause: Throwable
    ) : AdbShellException(
        message = "ADB connection failed: ${cause.message}",
        command = command,
        cause = cause
    )

    /**
     * The command execution failed for an unknown reason.
     */
    class UnknownException(
        command: String,
        message: String,
        cause: Throwable? = null
    ) : AdbShellException(
        message = message,
        command = command,
        cause = cause
    )
}
