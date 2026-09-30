package com.neilturner.perfview.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Permission to draw the process list on top of other apps, needed by the background overlay.
 *
 * Unlike a runtime permission this cannot be requested with a dialog. The only way to grant it
 * is a trip to Settings, so the intro checklist sends the user there rather than showing a prompt.
 */
interface OverlayAccessChecker {
    fun canDrawOverlays(): Boolean

    /**
     * The intent that opens the screen where the grant is toggled.
     *
     * Always returns an intent, matching the pattern that works in AerialViews: a bare
     * `Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)` with no data URI and no
     * FLAG_ACTIVITY_NEW_TASK. Deciding up front whether it is resolvable was the mistake that
     * silently skipped the prompt, so the launch itself is guarded by the caller instead.
     */
    fun createGrantIntent(): Intent
}

class AndroidOverlayAccessChecker(
    private val context: Context,
) : OverlayAccessChecker {

    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    override fun createGrantIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
}
