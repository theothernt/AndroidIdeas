package com.neilturner.perfview.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Notification access, which the foreground service notification depends on.
 *
 * Exposed behind an interface so the intro gate can be exercised without an Activity.
 */
interface NotificationPermissionChecker {
    /**
     * False below Android 13, where notifications need no runtime grant. The checklist reports
     * that as "not needed" rather than asking for something the platform cannot ask for.
     */
    fun isRequired(): Boolean

    fun isGranted(): Boolean
}

class AndroidNotificationPermissionChecker(
    private val context: Context,
) : NotificationPermissionChecker {

    override fun isRequired(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun isGranted(): Boolean =
        !isRequired() || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
}