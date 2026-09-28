package com.neilturner.perfview.overlay

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neilturner.perfview.domain.cpu.CpuMonitor
import com.neilturner.perfview.domain.cpu.repository.CpuRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

/**
 * Guards the pairing between the foreground service type the service promotes itself
 * with and the type declared in the manifest.
 *
 * Android throws IllegalArgumentException from startForeground() when the runtime type
 * is not a bitwise subset of `android:foregroundServiceType`. The two live in different
 * files and neither the compiler nor lint checks the relationship, so a mismatch ships
 * silently and only crashes once a user reaches the overlay hand-off.
 */
@RunWith(AndroidJUnit4::class)
class CpuOverlayServiceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private var originalOverlayAppOp: String? = null

    @Before
    fun setUp() {
        // The service is process-scoped, so a previous test may have left it attached.
        context.stopService(CpuOverlayService.createStopIntent(context))

        originalOverlayAppOp = readOverlayAppOp()
        // onStartCommand refuses to start without SYSTEM_ALERT_WINDOW, and granting it
        // needs the appops shell command, which UiAutomation can run for the
        // instrumentation.
        runShell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")

        // Swap in a repository that never dials ADB, so starting the service cannot
        // raise the system "Allow USB debugging?" dialog and steal focus from the
        // Compose tests that share this process.
        loadKoinModules(noAdbModule)
    }

    @After
    fun tearDown() {
        context.stopService(CpuOverlayService.createStopIntent(context))
        unloadKoinModules(noAdbModule)
        runShell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW ${originalOverlayAppOp ?: "deny"}")
    }

    @Test
    fun runtimeForegroundServiceType_isSubsetOfManifestDeclaration() {
        val declared = declaredForegroundServiceType()
        val runtime = CpuOverlayService.currentForegroundServiceType()

        assertNotEquals(
            "Manifest declares no foregroundServiceType, so startForeground has no valid type to use",
            0,
            declared,
        )
        assertTrue(
            "startForeground type 0x${runtime.toString(16)} is not a subset of the manifest " +
                "declaration 0x${declared.toString(16)}; startForeground() throws " +
                "IllegalArgumentException on this device",
            declared and runtime == runtime,
        )
    }
    @Test
    fun manifestDeclaresSpecialUse_soModernDevicesAvoidTheDataSyncCap() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return

        val declared = declaredForegroundServiceType()
        assertTrue(
            "specialUse must stay declared so API 34+ can use it; dataSync alone is capped " +
                "at six hours per day from Android 15",
            declared and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0,
        )
    }

    @Test
    fun service_startsAndPromotesToForegroundWithoutCrashing() = runBlocking {
        // Starting the service with a mismatched type crashes the test process, which
        // would abort the rest of the run. The static check above reports that
        // precisely, so stand down here and let it.
        assumeTrue("skipped: manifest and runtime foreground types disagree", manifestCoversRuntimeType())
        assertFalse("Service was already running before the test started it", CpuOverlayService.isRunning)

        context.startForegroundService(CpuOverlayService.createStartIntent(context))
        awaitOverlayRunning()

        assertTrue(CpuOverlayService.isRunning)
    }

    @Test
    fun service_stopsCleanly() = runBlocking {
        assumeTrue("skipped: manifest and runtime foreground types disagree", manifestCoversRuntimeType())

        context.startForegroundService(CpuOverlayService.createStartIntent(context))
        awaitOverlayRunning()

        context.stopService(CpuOverlayService.createStopIntent(context))

        withTimeout(TIMEOUT_MILLIS) {
            while (CpuOverlayService.isRunning) {
                delay(POLL_INTERVAL_MILLIS)
            }
        }

        assertFalse(CpuOverlayService.isRunning)
    }

    private fun manifestCoversRuntimeType(): Boolean {
        val declared = declaredForegroundServiceType()
        val runtime = CpuOverlayService.currentForegroundServiceType()
        return declared != 0 && declared and runtime == runtime
    }

    private suspend fun awaitOverlayRunning() {
        // A type mismatch throws inside startForeground() on the main thread, taking the
        // whole test process with it. Reaching the flag at all therefore proves the
        // promotion succeeded.
        withTimeout(TIMEOUT_MILLIS) {
            while (!CpuOverlayService.isRunning) {
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun declaredForegroundServiceType(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        return context.packageManager.getServiceInfo(
            ComponentName(context, CpuOverlayService::class.java),
            PackageManager.GET_META_DATA,
        ).foregroundServiceType
    }

    private fun readOverlayAppOp(): String? = runShell(
        "appops get ${context.packageName} SYSTEM_ALERT_WINDOW",
    )?.substringAfterLast(':')?.trim()?.takeIf { it.isNotEmpty() }

    private fun runShell(command: String): String? = runCatching {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }.getOrNull()

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_INTERVAL_MILLIS = 50L

        // module(override = true) lets these replace the ADB-backed definitions for the
        // duration of the test; positional because the parameter is not named override.
        val noAdbModule = module(true) {
            single<CpuRepository> { FakeCpuRepository() }
            single<CpuMonitor> { CpuMonitor(get()) }
        }
    }
}
