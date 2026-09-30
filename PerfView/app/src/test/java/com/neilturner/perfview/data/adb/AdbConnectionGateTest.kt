package com.neilturner.perfview.data.adb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cover for the process-wide ADB handshake ownership.
 *
 * The bug these guard against: cancelling a ViewModel mid-handshake used to abandon a connect
 * while it still owned the shared connection manager, leaving that manager believing it was
 * connected and failing every later attempt with a cancellation error.
 */
class AdbConnectionGateTest {

    @Test
    fun `session starts unauthorized`() {
        val gate = AdbConnectionGate(RecordingAccessManager())

        assertFalse(gate.isAuthorized)
    }

    @Test
    fun `successful handshake marks the session authorized`() = runTest {
        val gate = AdbConnectionGate(RecordingAccessManager())

        val result = gate.ensureAuthorized()

        assertTrue(result.isSuccess)
        assertTrue(gate.isAuthorized)
    }

    @Test
    fun `failed handshake leaves the session unauthorized`() = runTest {
        val gate = AdbConnectionGate(
            FailingAccessManager(AdbUnavailableException("wireless debugging is off")),
        )

        val result = gate.ensureAuthorized()

        assertTrue(result.isFailure)
        assertFalse(gate.isAuthorized)
    }

    @Test
    fun `an already authorized session does not handshake again`() = runTest {
        val access = RecordingAccessManager()
        val gate = AdbConnectionGate(access)

        gate.ensureAuthorized()
        gate.ensureAuthorized()

        assertEquals("only the first call should connect", 1, access.calls)
    }

    @Test
    fun `concurrent callers share one handshake`() = runTest {
        val access = RecordingAccessManager(gate = CompletableDeferred())
        val gate = AdbConnectionGate(access)

        val first = async { gate.ensureAuthorized() }
        val second = async { gate.ensureAuthorized() }
        access.releaseGate()
        first.await()
        second.await()

        assertEquals("concurrent callers must not each connect", 1, access.calls)
        assertTrue(gate.isAuthorized)
    }

    @Test
    fun `marking unauthorized forces the next call to handshake again`() = runTest {
        val access = RecordingAccessManager()
        val gate = AdbConnectionGate(access)

        gate.ensureAuthorized()
        gate.markUnauthorized()
        val result = gate.ensureAuthorized()

        assertTrue(result.isSuccess)
        assertTrue(gate.isAuthorized)
        assertEquals(2, access.calls)
    }

    private class RecordingAccessManager(
        private val gate: CompletableDeferred<Unit>? = null,
    ) : AdbAccessManager {
        var calls = 0
            private set

        override suspend fun requestAccess(timeoutMillis: Long) {
            calls += 1
            gate?.await()
        }

        /** Lets a blocked handshake complete, so a concurrent test can settle. */
        suspend fun releaseGate() {
            gate?.complete(Unit)
        }
    }

    private class FailingAccessManager(
        private val error: Exception,
    ) : AdbAccessManager {
        override suspend fun requestAccess(timeoutMillis: Long): Unit = throw error
    }
}