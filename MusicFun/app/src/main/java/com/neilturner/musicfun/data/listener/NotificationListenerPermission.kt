package com.neilturner.musicfun.data.listener

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Notification listener access is a special "restricted" setting: it cannot be requested with a
 * runtime permission dialog, the user has to enable the service in system settings.
 */
object NotificationListenerPermission {

	fun isGranted(context: Context): Boolean =
		NotificationManagerCompat.getEnabledListenerPackages(context)
			.contains(context.packageName)

	/**
	 * Opens the notification access settings, falling back to the generic settings app on devices
	 * that do not ship the listener screens (some Android TV builds).
	 */
	fun openSettings(context: Context) {
		settingsIntents(context).forEach { intent ->
			try {
				context.startSettingsActivity(intent)
				return
			} catch (_: ActivityNotFoundException) {
				// Try the next, less specific, settings screen.
			}
		}
	}

	private fun settingsIntents(context: Context): List<Intent> {
		val listenerSettings = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
		val genericSettings = Intent(Settings.ACTION_SETTINGS)

		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val component = ComponentName(context, MusicNotificationListenerService::class.java)
			listOf(
				Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
					.putExtra(
						Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
						component.flattenToString()
					),
				listenerSettings,
				genericSettings,
			)
		} else {
			listOf(listenerSettings, genericSettings)
		}
	}

	private fun Context.startSettingsActivity(intent: Intent) {
		if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		startActivity(intent)
	}
}