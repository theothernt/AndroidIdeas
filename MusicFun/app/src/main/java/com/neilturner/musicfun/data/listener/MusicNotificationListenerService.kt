package com.neilturner.musicfun.data.listener

import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.neilturner.musicfun.data.MusicMetadata
import com.neilturner.musicfun.data.MusicMetadataRepository

/**
 * Reads "now playing" metadata out of the media notifications posted by music apps.
 *
 * A media notification carries a [MediaSession.Token]; wrapping it in a [MediaController] gives us
 * the track title/artist/album, both as a snapshot and as updates via [MediaController.Callback].
 */
class MusicNotificationListenerService : NotificationListenerService() {

	private class Session(
		val packageName: String,
		val controller: MediaController,
		val callback: MediaController.Callback,
	)

	private val mainHandler = Handler(Looper.getMainLooper())
	private val sessions = mutableMapOf<MediaSession.Token, Session>()
	private var currentToken: MediaSession.Token? = null

	private var tickerRunning = false

	/** Polls the controller's PlaybackState so the UI can interpolate the playhead. */
	private val positionTicker = object : Runnable {
		override fun run() {
			val controller = sessions[currentToken]?.controller
			if (controller == null) {
				tickerRunning = false
				return
			}
			val state = controller.playbackState
			if (state != null) {
				val playing = state.state == PlaybackState.STATE_PLAYING
				val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
				val interpolated = state.position +
					(elapsed * state.playbackSpeed).toLong()
				val position = if (playing && state.playbackSpeed > 0f && elapsed in 0..5_000) {
					interpolated
				} else {
					state.position
				}
				MusicMetadataRepository.updatePlayback(playing, position)
			}
			mainHandler.postDelayed(this, 250)
		}
	}

	private fun startTicker() {
		if (tickerRunning) return
		tickerRunning = true
		mainHandler.post(positionTicker)
	}

	private fun stopTicker() {
		tickerRunning = false
		mainHandler.removeCallbacks(positionTicker)
		MusicMetadataRepository.updatePlayback(false, 0L)
	}

	override fun onListenerConnected() {
		// Catch anything that was already playing before the service was (re)bound.
		activeNotifications.forEach(::onNotificationPosted)
	}

	override fun onNotificationPosted(sbn: StatusBarNotification) {
		val token = sbn.mediaSessionToken() ?: return

		val existing = sessions[token]
		if (existing != null) {
			publish(existing.packageName, existing.controller.metadata)
			startTicker()
			return
		}

		val controller = try {
			MediaController(this, token)
		} catch (_: SecurityException) {
			// We only get the session token while notification access is enabled, so this means the
			// user revoked it between the notification being posted and now.
			return
		}

		val callback = object : MediaController.Callback() {
			override fun onMetadataChanged(metadata: MediaMetadata?) {
				if (currentToken == token) publish(sbn.packageName, metadata)
			}
		}

		controller.registerCallback(callback, mainHandler)
		sessions[token] = Session(sbn.packageName, controller, callback)
		currentToken = token
		publish(sbn.packageName, controller.metadata)
		startTicker()
	}

	override fun onNotificationRemoved(sbn: StatusBarNotification) {
		val token = sbn.mediaSessionToken() ?: return
		val removed = sessions.remove(token) ?: return
		removed.controller.unregisterCallback(removed.callback)

		if (currentToken == token) {
			val next = sessions.entries.firstOrNull()
			currentToken = next?.key
			if (next == null) {
				MusicMetadataRepository.clear()
				stopTicker()
			} else {
				publish(next.value.packageName, next.value.controller.metadata)
				startTicker()
			}
		}
	}

	override fun onListenerDisconnected() {
		clearSessions()
	}

	override fun onDestroy() {
		clearSessions()
		super.onDestroy()
	}

	private fun clearSessions() {
		sessions.values.forEach { it.controller.unregisterCallback(it.callback) }
		sessions.clear()
		currentToken = null
		stopTicker()
	}

	private fun publish(packageName: String, metadata: MediaMetadata?) {
		val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
		val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
			?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
		val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)

		if (title.isNullOrBlank() && artist.isNullOrBlank()) return

		val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
			?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
		val artworkUri = metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
			?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
		val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
			?.takeIf { it > 0 }

		MusicMetadataRepository.update(
			MusicMetadata(
				packageName = packageName,
				title = title,
				artist = artist,
				album = album,
				artwork = artwork,
				artworkUri = artworkUri,
				durationMs = durationMs,
			)
		)
	}

	private fun StatusBarNotification.mediaSessionToken(): MediaSession.Token? {
		val extras = notification.extras
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
		} else {
			@Suppress("DEPRECATION")
			extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
		}
	}
}