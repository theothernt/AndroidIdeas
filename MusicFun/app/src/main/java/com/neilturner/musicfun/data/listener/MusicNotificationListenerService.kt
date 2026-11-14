package com.neilturner.musicfun.data.listener

import android.app.Notification
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.neilturner.musicfun.data.MusicMetadata
import com.neilturner.musicfun.data.MusicMetadataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads "now playing" metadata out of the media notifications posted by music apps.
 *
 * A media notification carries a [MediaSession.Token]; wrapping it in a [MediaController] gives us
 * the track title/artist/album, both as a snapshot and as updates via [MediaController.Callback].
 *
 * All binder calls (creating controllers, reading metadata/playback state) run on an IO scope: the
 * service shares the app's main thread with the UI, and a blocked binder call here freezes the UI.
 */
class MusicNotificationListenerService : NotificationListenerService() {

	private class Session(
		val packageName: String,
		val controller: MediaController,
		val callback: MediaController.Callback,
	)

	private val mainHandler = Handler(Looper.getMainLooper())
	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val sessions = ConcurrentHashMap<MediaSession.Token, Session>()

	@Volatile
	private var currentToken: MediaSession.Token? = null

	private var tickerJob: Job? = null

	/** Keeps the screen on while music plays so the system screensaver/dream stays away. */
	private val screenWakeLock: PowerManager.WakeLock by lazy {
		val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
		@Suppress("DEPRECATION")
		powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "MusicFun:Playback")
			.apply { setReferenceCounted(false) }
	}

	override fun onListenerConnected() {
		ioScope.launch {
			// Catch anything that was already playing before the service was (re)bound.
			activeNotifications.forEach(::onNotificationPosted)
		}
	}

	override fun onNotificationPosted(sbn: StatusBarNotification) {
		val token = sbn.mediaSessionToken() ?: return
		if (sessions.containsKey(token)) return

		ioScope.launch {
			val controller = try {
				MediaController(this@MusicNotificationListenerService, token)
			} catch (_: SecurityException) {
				return@launch
			}

			val callback = object : MediaController.Callback() {
				override fun onMetadataChanged(metadata: MediaMetadata?) {
					if (currentToken == token) publish(sbn.packageName, metadata)
				}
			}

			controller.registerCallback(callback, mainHandler)
			sessions[token] = Session(sbn.packageName, controller, callback)
			if (currentToken == null) currentToken = token
			startTicker()
		}
	}

	override fun onNotificationRemoved(sbn: StatusBarNotification) {
		val token = sbn.mediaSessionToken() ?: return
		val removed = sessions.remove(token) ?: return
		ioScope.launch { removed.controller.unregisterCallback(removed.callback) }

		if (currentToken == token) {
			val next = sessions.entries.firstOrNull()
			currentToken = next?.key
			if (next == null) {
				MusicMetadataRepository.clear()
				stopTicker()
			}
		}
	}

	override fun onListenerDisconnected() {
		clearSessions()
	}

	override fun onDestroy() {
		clearSessions()
		ioScope.cancel()
		super.onDestroy()
	}

	private fun clearSessions() {
		val toUnregister = sessions.values.toList()
		sessions.clear()
		currentToken = null
		ioScope.launch { toUnregister.forEach { it.controller.unregisterCallback(it.callback) } }
		if (screenWakeLock.isHeld) screenWakeLock.release()
		stopTicker()
	}

	private fun startTicker() {
		if (tickerJob?.isActive == true) return
		tickerJob = ioScope.launch {
			while (isActive) {
				updatePlayback()
				delay(250)
			}
		}
	}

	private fun stopTicker() {
		tickerJob?.cancel()
		tickerJob = null
		MusicMetadataRepository.updatePlayback(false, 0L)
		if (screenWakeLock.isHeld) screenWakeLock.release()
	}

	private fun updatePlayback() {
		val session = sessions[currentToken] ?: return
		val controller = session.controller

		// Keep the metadata fresh even if the notification isn't re-posted.
		val metadata = controller.metadata
		if (metadata != null) publish(session.packageName, metadata)

		val state = controller.playbackState ?: return
		val playing = state.state == PlaybackState.STATE_PLAYING
		val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
		val interpolated = state.position + (elapsed * state.playbackSpeed).toLong()
		val position = if (playing && state.playbackSpeed > 0f && elapsed in 0..5_000) {
			interpolated
		} else {
			state.position
		}
		MusicMetadataRepository.updatePlayback(playing, position)

		if (playing) {
			if (!screenWakeLock.isHeld) screenWakeLock.acquire()
		} else if (screenWakeLock.isHeld) {
			screenWakeLock.release()
		}
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