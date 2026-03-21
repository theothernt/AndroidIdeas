package com.neilturner.perfview.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

class OverlayPermissionManager(
    private val context: Context,
) {
    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun createPermissionIntent(): Intent {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            intent.data = Uri.parse("package:${context.packageName}")
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
