package com.neilturner.perfview.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.neilturner.perfview.MainActivity
import com.neilturner.perfview.R
import com.neilturner.perfview.domain.cpu.CpuMonitor
import com.neilturner.perfview.domain.cpu.CpuUsageResult
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class CpuOverlayService : Service() {
    private val cpuMonitor: CpuMonitor by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private var overlayView: LinearLayout? = null
    private val rowViews = mutableListOf<TextView>()
    private var observeJob: Job? = null
    private var isMonitoringActive = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!OverlayPermissionManager(this).canDrawOverlays()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForegroundService()
        ensureOverlayAttached()
        startObserving()
        return START_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        stopMonitoring()
        removeOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForegroundService() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            createStopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Perf View overlay running")
            .setContentText("Showing the top 4 CPU processes on screen.")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Perf View overlay",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun ensureOverlayAttached() {
        if (overlayView != null) return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(18)
                setColor(0xF2102A36.toInt())
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        repeat(5) {
            val row = TextView(this).apply {
                setTextColor(0xFFEAF5F7.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(0, if (it == 0) 0 else dp(4), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                text = "--"
            }
            rowViews += row
            container.addView(row)
        }

        val params = WindowManager.LayoutParams(
            dp(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(12)
        }

        overlayView = container
        windowManager.addView(container, params)
    }

    private fun startObserving() {
        observeJob?.cancel()
        startMonitoring()
        cpuMonitor.results.value?.let(::renderResult)
        observeJob = serviceScope.launch {
            cpuMonitor.results.collectLatest { result ->
                result?.let(::renderResult)
            }
        }
    }

    private fun renderResult(result: CpuUsageResult) {
        when (result) {
            is CpuUsageResult.Success -> updateRows(
                result.observation.topProcesses
                    .take(5)
                    .mapIndexed { index, process ->
                        "${index + 1}. ${formatCpu(process.cpuPercent)}  ${formatRamMb(process.ramMb)}  ${process.name}"
                    }
            )

            is CpuUsageResult.Unsupported -> updateRows(
                listOf(
                    "ADB unavailable",
                    result.message,
                )
            )
        }
    }

    private fun updateRows(lines: List<String>) {
        rowViews.forEachIndexed { index, textView ->
            textView.text = lines.getOrNull(index) ?: ""
        }
    }

    private fun startMonitoring() {
        if (isMonitoringActive) return
        cpuMonitor.acquire()
        isMonitoringActive = true
    }

    private fun stopMonitoring() {
        if (!isMonitoringActive) return
        cpuMonitor.release()
        isMonitoringActive = false
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            if (view.isAttachedToWindow) {
                windowManager.removeView(view)
            }
        }
        overlayView = null
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()

    private fun dpF(value: Int): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        )

    private fun formatCpu(value: Float): String = String.format(Locale.US, "%.0f%%", value)

    private fun formatRamMb(value: Float): String = String.format(Locale.US, "%.0fMB", value)

    companion object {
        private const val ACTION_START = "com.neilturner.perfview.overlay.START"
        private const val ACTION_STOP = "com.neilturner.perfview.overlay.STOP"
        private const val NOTIFICATION_CHANNEL_ID = "perfview_overlay"
        private const val NOTIFICATION_ID = 1001

        fun createStartIntent(context: Context): Intent =
            Intent(context, CpuOverlayService::class.java).setAction(ACTION_START)

        fun createStopIntent(context: Context): Intent =
            Intent(context, CpuOverlayService::class.java).setAction(ACTION_STOP)
    }
}
