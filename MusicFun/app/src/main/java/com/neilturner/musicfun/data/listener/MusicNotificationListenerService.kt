package com.neilturner.musicfun.data.listener

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
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
 * Reads "now playing" metadata from the active media sessions.
 *
 * Notification listener access is required to call [MediaSessionManager.getActiveSessions], and it is
 * also what lets us wrap a session token in a [MediaController]. All binder work runs on an IO scope
 * so the service never blocks the app's main thread.
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

	@Volatile
	private var lastPlaybackState: PlaybackState? = null

	@Volatile
	private var lastStateAtElapsed = 0L

	private var tickerJob: Job? = null

	private val screenWakeLock: PowerManager.WakeLock by lazy {
		val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
		@Suppress("DEPRECATION")
		powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "MusicFun:Playback")
			.apply { setReferenceCounted(false) }
	}

	override fun onListenerConnected() {
		ioScope.launch { refreshSessions() }
	}

	override fun onListenerDisconnected() {
		clearSessions()
	}

	override fun onDestroy() {
		clearSessions()
		ioScope.cancel()
		super.onDestroy()
	}

	/** Add/remove sessions to match the system's active list, then make sure we have a ticker. */
	private fun refreshSessions() {
		val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
		val controllers = manager.getActiveSessions(
			ComponentName(this, MusicNotificationListenerService::class.java)
		)
		val tokens = controllers.map { it.sessionToken }.toSet()

		sessions.keys.filter { it !in tokens }.forEach(::removeSession)
		controllers.forEach(::addSession)

		if (currentToken !in tokens) {
			currentToken = tokens.firstOrNull()
			if (currentToken == null) MusicMetadataRepository.clear()
		}

		startTicker()
	}

	private fun addSession(controller: MediaController) {
		val token = controller.sessionToken
		if (sessions.containsKey(token)) return

		val callback = object : MediaController.Callback() {
			override fun onMetadataChanged(metadata: MediaMetadata?) {
				if (currentToken == token) publish(controller.packageName, metadata)
			}

			override fun onPlaybackStateChanged(state: PlaybackState?) {
				lastPlaybackState = state
				lastStateAtElapsed = SystemClock.elapsedRealtime()
			}
		}

		controller.registerCallback(callback, mainHandler)
		sessions[token] = Session(controller.packageName, controller, callback)
		if (currentToken == null) currentToken = token
	}

	private fun removeSession(token: MediaSession.Token) {
		val session = sessions.remove(token) ?: return
		session.controller.unregisterCallback(session.callback)
		if (currentToken == token) currentToken = sessions.keys.firstOrNull()
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
			var tick = 0
			while (isActive) {
				updatePlayback()
				if (tick % 4 == 0) refreshSessions()
				tick++
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

		val metadata = controller.metadata
		if (metadata != null) publish(session.packageName, metadata)

		val state = lastPlaybackState ?: controller.playbackState ?: return
		val playing = state.state == PlaybackState.STATE_PLAYING
		val elapsed = SystemClock.elapsedRealtime() - lastStateAtElapsed
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
}