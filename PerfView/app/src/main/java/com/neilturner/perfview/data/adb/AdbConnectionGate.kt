package com.neilturner.perfview.data.adb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide owner of the ADB handshake.
 *
 * Two problems this exists to solve:
 *
 * 1. Authorization is remembered for the life of the process. The overlay service keeps the
 *    process alive, so a returning Activity can go straight to the process list instead of
 *    replaying the gate on every foreground/background cycle.
 *
 * 2. The handshake is not owned by any ViewModel. It runs in this scope, so cancelling a
 *    ViewModel cannot abandon a connect part way through. Doing that tears down the socket
 *    inside the shared [PerfViewAdbConnectionManager] while it still believes it is connected,
 *    and every later connection then fails with a cancellation error instead of reconnecting.
 *
 * The connection manager is a process-wide singleton, so its handshake has to be too.
 */
class AdbConnectionGate(
    private val adbAccessManager: AdbAccessManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Guards the handshake so concurrent callers await one attempt rather than racing two
     * connections against the same manager.
     */
    private val handshakeMutex = Mutex()

    @Volatile
    private var inFlight: Deferred<Result<Unit>>? = null

    /**
     * True once a connection has been authorized in this process. Reset only by process death,
     * which is also the only thing that invalidates the app's ADB identity.
     */
    @Volatile
    var isAuthorized: Boolean = false
        private set

    /**
     * Ensures an authorized, verified ADB session, reusing the in-flight attempt if one is
     * already under way. Never throws for an expected ADB failure: those come back as a failed
     * [Result] so callers can render them.
     *
     * [timeoutMillis] bounds how long this caller waits, and deliberately does not bound the
     * handshake itself, since abandoning it would corrupt the shared connection.
     */
    suspend fun ensureAuthorized(timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): Result<Unit> {
        val existing = inFlight
        if (existing != null && !existing.isCompleted) {
            return runCatching { existing.await() }.getOrElse { Result.failure(it) }
        }

        val attempt = handshakeMutex.withLock {
            // Re-check inside the lock: a caller may have completed a handshake while this one
            // was waiting for it, in which case there is nothing left to do.
            if (isAuthorized) return Result.success(Unit)

            val started = scope.async {
                runCatching { adbAccessManager.requestAccess(timeoutMillis = NO_TIMEOUT) }
            }
            inFlight = started
            started
        }

        val result = runCatching { attempt.await() }.getOrElse { Result.failure(it) }
        if (result.isSuccess) {
            isAuthorized = true
        }
        return result
    }

    /**
     * Marks the session unauthorized, for example after a read failure that indicates the
     * device or its debugging authorisation went away. The next call will handshake again.
     */
    fun markUnauthorized() {
        isAuthorized = false
    }

    fun close() {
        scope.cancel()
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L

        /**
         * Passed to the access manager for the underlying attempt, which is not abandoned. The
         * caller's own timeout is enforced by the awaiting side instead.
         */
        const val NO_TIMEOUT = Long.MAX_VALUE
    }
}