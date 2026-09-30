package com.neilturner.perfview.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Permission to draw the process list on top of other apps, needed by the background overlay.
 *
 * Unlike a runtime permission this cannot be requested with a dialog. The only way to grant it
 * is a trip to Settings, so the intro checklist sends the user there rather than showing a prompt.
 */
interface OverlayAccessChecker {
    fun canDrawOverlays(): Boolean

    fun createGrantIntent(): Intent
}

class AndroidOverlayAccessChecker(
    private val context: Context,
) : OverlayAccessChecker {

    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    override fun createGrantIntent(): Intent {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            intent.data = Uri.parse("package:${context.packageName}")
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
