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
import com.neilturner.musicfun.data.PlaybackStatus
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
	) {
		lateinit var callback: MediaController.Callback
	}

	private val mainHandler = Handler(Looper.getMainLooper())
	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val sessions = ConcurrentHashMap<MediaSession.Token, Session>()

	@Volatile
	private var currentToken: MediaSession.Token? = null

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

		val session = Session(controller.packageName, controller)
		session.callback = object : MediaController.Callback() {
			override fun onMetadataChanged(metadata: MediaMetadata?) {
				if (currentToken == token) publish(controller.packageName, metadata)
			}

			// Publish straight away rather than waiting for the ticker, so pausing hides the
			// now-playing info promptly. Binder reads happen on the IO scope, not here.
			override fun onPlaybackStateChanged(state: PlaybackState?) {
				if (currentToken == token) ioScope.launch { updatePlayback() }
			}
		}

		controller.registerCallback(session.callback, mainHandler)
		sessions[token] = session
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
		MusicMetadataRepository.updatePlayback(PlaybackStatus.Stopped, 0L)
		if (screenWakeLock.isHeld) screenWakeLock.release()
	}

	private fun updatePlayback() {
		val session = sessions[currentToken]
		if (session == null) {
			// The media app is gone: don't leave the last "playing" state behind.
			MusicMetadataRepository.updatePlayback(PlaybackStatus.Stopped, 0L)
			if (screenWakeLock.isHeld) screenWakeLock.release()
			return
		}

		val controller = session.controller

		val metadata = controller.metadata
		if (metadata != null) publish(session.packageName, metadata)

		// Read the live state rather than caching callbacks: a missed callback would otherwise pin
		// the UI to a stale "playing" position.
		val state = controller.playbackState
		if (state == null) {
			MusicMetadataRepository.updatePlayback(PlaybackStatus.Stopped, 0L)
			if (screenWakeLock.isHeld) screenWakeLock.release()
			return
		}

		val status = when (state.state) {
			// Buffering counts as playing so a skip doesn't blank the info for a beat.
			PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> PlaybackStatus.Playing
			PlaybackState.STATE_PAUSED -> PlaybackStatus.Paused
			else -> PlaybackStatus.Stopped
		}

		// Players such as Spotify TV only publish a state when it changes, so the position keeps
		// running from that last report; the old five-second cap froze the lyrics mid-track. The
		// track duration is where a stale session stops: it can never outrun its own track.
		val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }
		val position = if (status == PlaybackStatus.Playing && state.playbackSpeed > 0f) {
			positionAt(state)
		} else {
			state.position
		}
		MusicMetadataRepository.updatePlayback(
			status,
			position.coerceIn(0L, durationMs ?: Long.MAX_VALUE),
		)

		if (status == PlaybackStatus.Playing) {
			if (!screenWakeLock.isHeld) screenWakeLock.acquire()
		} else if (screenWakeLock.isHeld) {
			screenWakeLock.release()
		}
	}

	/**
	 * Position right now for a reported state: media apps only publish one when it changes, so the
	 * time since that report is added on. A state without a report time is used unchanged.
	 */
	private fun positionAt(state: PlaybackState): Long {
		val reportedAt = state.lastPositionUpdateTime
		if (reportedAt <= 0) return state.position
		val elapsed = (SystemClock.elapsedRealtime() - reportedAt).coerceAtLeast(0L)
		return state.position + (elapsed * state.playbackSpeed).toLong()
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
