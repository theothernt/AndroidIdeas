package com.neilturner.musicfun.data.facts

import com.neilturner.musicfun.data.MusicMetadata
import com.neilturner.musicfun.data.track.TrackMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A calendar date at whatever precision MusicBrainz has; month and day default to 1. */
data class ReleaseDate(val year: Int, val month: Int = 1, val day: Int = 1)

/**
 * Finds when a track was first released.
 *
 * Apple's catalog is asked first: its dates survive remasters and compilations ("Greatest Hits" still
 * carries the original year), which MusicBrainz's release data often misses. MusicBrainz is the
 * fallback when Apple has no match, and its recording/release-group dates are used the same way.
 * Lookups are rate-limited, serialised and cached by artist/title/album; titles and artists are
 * cleaned with [TrackMatch] so a "- 2007 Remaster" suffix still matches the original.
 */
object ReleaseDateRepository {

	private const val BaseUrl = "https://musicbrainz.org/ws/2"
	private const val ItunesUrl = "https://itunes.apple.com/search"
	private const val UserAgent = "MusicFun/1.0 (Android)"
	private const val MinIntervalMs = 1_100L
	private const val TimeoutMs = 8_000
	private const val RetryDelayMs = 1_500L

	/** "Greatest Hits", "The Collection", "Best Of …": the release group is not the song's date. */
	private val CompilationWords = Regex(
		"(?i)\\b(greatest hits|best of|the best|very best|essential|collection|anthology|hits|compilation)\\b"
	)

	private val cache = mutableMapOf<String, ReleaseDate?>()
	private val rateLock = Mutex()
	private var lastRequestAt = 0L

	suspend fun fetch(metadata: MusicMetadata): ReleaseDate? = withContext(Dispatchers.IO) {
		val artist = metadata.artist?.trim().orEmpty()
		val title = metadata.title?.trim().orEmpty()
		if (artist.isEmpty() || title.isEmpty()) return@withContext null

		val album = metadata.album?.trim().orEmpty()
		val key = TrackMatch.normalize(artist) + "|" + TrackMatch.normalize(title) +
			"|" + TrackMatch.normalize(album)
		synchronized(cache) {
			if (cache.containsKey(key)) return@withContext cache[key]
		}

		val result = runCatching { fetchNetwork(artist, title, album) }.getOrNull()
		synchronized(cache) { cache[key] = result }
		result
	}

	private suspend fun fetchNetwork(artist: String, title: String, album: String): ReleaseDate? =
		itunesDate(artist, title) ?: musicBrainzDate(artist, title, album)

	/**
	 * Apple keeps the original date on remasters and hits collections, where a release-based source
	 * would report the reissue. Its search returns one row per album/single the track appears on, so
	 * the earliest match is the song's first release; live versions and remixes are always later.
	 */
	private suspend fun itunesDate(artist: String, title: String): ReleaseDate? {
		awaitRateSlot()

		val term = TrackMatch.mainArtist(artist) + " " + TrackMatch.cleanTitle(title)
		val json = getJson("$ItunesUrl?term=${enc(term)}&entity=song&media=music&limit=25")
			?: return null
		val results = JSONObject(json).optJSONArray("results") ?: return null
		val wantedArtist = TrackMatch.normalize(TrackMatch.mainArtist(artist))
		var earliest: ReleaseDate? = null
		for (index in 0 until results.length()) {
			val row = results.optJSONObject(index) ?: continue
			val rowArtist = TrackMatch.normalize(row.optString("artistName"))
			if (!rowArtist.contains(wantedArtist)) continue
			if (!titlesMatch(title, row.optString("trackName"))) continue
			val date = parseDate(row.optString("releaseDate").take(10)) ?: continue
			earliest = earlierOf(earliest, date)
		}
		return earliest
	}

	/** Apple spells the same song differently ("- Original Instrumental", "APT."), so be lenient. */
	private fun titlesMatch(ours: String, theirs: String): Boolean {
		val a = TrackMatch.normalize(TrackMatch.cleanTitle(ours))
		val b = TrackMatch.normalize(TrackMatch.cleanTitle(theirs))
		if (a.isEmpty() || b.isEmpty()) return false
		if (a == b) return true
		val shorter = if (a.length <= b.length) a else b
		val longer = if (a.length <= b.length) b else a
		return shorter.length >= 4 && longer.startsWith(shorter)
	}

	private suspend fun musicBrainzDate(artist: String, title: String, album: String): ReleaseDate? {
		// The album's release group usually holds the original date while the recordings of an old
		// song often only list later reissues, so ask both and keep whichever is earlier.
		var earliest = recordingDate(artist, title)
		// A hits collection's release group is the compilation's date, not the song's; when the
		// recording lookup came back empty that would show a decade-old song as recent.
		if (album.isNotBlank() && !CompilationWords.containsMatchIn(TrackMatch.cleanTitle(album))) {
			earliest = earlierOf(earliest, releaseGroupDate(artist, album))
		}
		return earliest
	}

	private suspend fun recordingDate(artist: String, title: String): ReleaseDate? {
		awaitRateSlot()

		val cleanedTitle = TrackMatch.cleanTitle(title)
		val mainArtist = TrackMatch.mainArtist(artist)
		val query = "recording:\"$cleanedTitle\" AND artist:\"$mainArtist\""
		val json = getJson("$BaseUrl/recording/?query=${enc(query)}&fmt=json&limit=10")
			?: return null
		val recordings = JSONObject(json).optJSONArray("recordings") ?: return null

		val wantedTitle = TrackMatch.normalize(cleanedTitle)
		val wantedArtist = TrackMatch.normalize(mainArtist)
		var earliest: ReleaseDate? = null

		for (index in 0 until recordings.length()) {
			val recording = recordings.optJSONObject(index) ?: continue
			val candidateTitle = TrackMatch.normalize(TrackMatch.cleanTitle(recording.optString("title")))
			if (candidateTitle != wantedTitle) continue
			if (!hasArtist(recording, wantedArtist)) continue

			val releases = recording.optJSONArray("releases") ?: continue
			for (releaseIndex in 0 until releases.length()) {
				val release = releases.optJSONObject(releaseIndex) ?: continue
				earliest = earlierOf(earliest, parseDate(release.optString("date")))
				val groupDate = release.optJSONObject("release-group")
					?.optString("first-release-date")
					.orEmpty()
				earliest = earlierOf(earliest, parseDate(groupDate))
			}
		}

		return earliest
	}

	/** First release of the album the track came from, matched by its cleaned title and artist. */
	private suspend fun releaseGroupDate(artist: String, album: String): ReleaseDate? {
		awaitRateSlot()

		val cleanedAlbum = TrackMatch.cleanTitle(album)
		val mainArtist = TrackMatch.mainArtist(artist)
		val query = "releasegroup:\"$cleanedAlbum\" AND artist:\"$mainArtist\""
		val json = getJson("$BaseUrl/release-group/?query=${enc(query)}&fmt=json&limit=10")
			?: return null
		val groups = JSONObject(json).optJSONArray("release-groups") ?: return null

		val wantedAlbum = TrackMatch.normalize(cleanedAlbum)
		val wantedArtist = TrackMatch.normalize(mainArtist)
		var earliest: ReleaseDate? = null
		for (index in 0 until groups.length()) {
			val group = groups.optJSONObject(index) ?: continue
			if (TrackMatch.normalize(group.optString("title")) != wantedAlbum) continue
			if (!hasArtist(group, wantedArtist)) continue
			earliest = earlierOf(earliest, parseDate(group.optString("first-release-date")))
		}
		return earliest
	}

	private fun hasArtist(recording: JSONObject, wantedArtist: String): Boolean {
		if (wantedArtist.isEmpty()) return false
		val credit = recording.optJSONArray("artist-credit") ?: return false
		for (index in 0 until credit.length()) {
			val name = TrackMatch.normalize(credit.optJSONObject(index)?.optString("name").orEmpty())
			if (name.isNotEmpty() && (name.contains(wantedArtist) || wantedArtist.contains(name))) {
				return true
			}
		}
		return false
	}

	/** MusicBrainz dates come as YYYY, YYYY-MM or YYYY-MM-DD. */
	private fun parseDate(value: String): ReleaseDate? {
		val parts = value.split("-")
		val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
		if (year < 1000) return null
		val month = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 1
		val day = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 31) ?: 1
		return ReleaseDate(year, month, day)
	}

	private fun earlierOf(current: ReleaseDate?, candidate: ReleaseDate?): ReleaseDate? {
		if (candidate == null) return current
		if (current == null) return candidate
		return if (candidate.sortKey() < current.sortKey()) candidate else current
	}

	private fun ReleaseDate.sortKey(): Int = year * 10_000 + month * 100 + day

	/** MusicBrainz allows one call per second per client; keep a margin for clock drift. */
	private suspend fun awaitRateSlot() {
		rateLock.withLock {
			val wait = MinIntervalMs - (System.currentTimeMillis() - lastRequestAt)
			if (wait > 0) delay(wait)
			lastRequestAt = System.currentTimeMillis()
		}
	}

	/** One retry: MusicBrainz answers 503 when it wants a client to slow down. */
	private suspend fun getJson(url: String): String? {
		repeat(2) { attempt ->
			val body = requestOnce(url)
			if (body != null) return body
			if (attempt == 0) delay(RetryDelayMs)
		}
		return null
	}

	private fun requestOnce(url: String): String? {
		val connection = (URL(url).openConnection() as HttpURLConnection).apply {
			requestMethod = "GET"
			setRequestProperty("User-Agent", UserAgent)
			setRequestProperty("Accept", "application/json")
			connectTimeout = TimeoutMs
			readTimeout = TimeoutMs
		}
		return try {
			if (connection.responseCode != HttpURLConnection.HTTP_OK) {
				null
			} else {
				connection.inputStream.bufferedReader().use { it.readText() }
			}
		} catch (_: Exception) {
			null
		} finally {
			connection.disconnect()
		}
	}

	private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
