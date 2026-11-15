package com.neilturner.musicfun.data.lyrics

import com.neilturner.musicfun.data.MusicMetadata
import com.neilturner.musicfun.data.track.TrackMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

/**
 * Looks up lyrics on LRCLIB (https://lrclib.net). Streaming metadata is messy (feat., remaster
 * suffixes, multiple artists), so the title/artist are cleaned and every search hit is verified before
 * it is accepted — otherwise a similarly named song gets shown instead of the one playing. Synced
 * lines also need a known, closely matching track duration, and plain lyrics are preferred over a
 * hit whose timings cannot be trusted.
 */
object LyricsRepository {

	private const val BaseUrl = "https://lrclib.net/api"
	private const val UserAgent = "MusicFun/1.0 (Android)"

	/** Seconds a synced hit's duration may differ and still have its timings trusted. */
	private const val SyncedToleranceSec = 2.0

	private val cache = mutableMapOf<String, LyricsResult>()

	suspend fun fetch(metadata: MusicMetadata): LyricsResult = withContext(Dispatchers.IO) {
		val artist = metadata.artist?.trim().orEmpty()
		val title = metadata.title?.trim().orEmpty()
		if (artist.isEmpty() || title.isEmpty()) return@withContext LyricsResult.None

		val seconds = metadata.durationMs?.div(1000)?.toInt() ?: 0
		val key = "$artist|$title|${metadata.album}|$seconds"
		cache[key]?.let { return@withContext it }

		val result = runCatching { fetchNetwork(artist, title, metadata.album, seconds) }
			.getOrElse { LyricsResult.None }
		cache[key] = result
		result
	}

	private fun fetchNetwork(artist: String, title: String, album: String?, seconds: Int): LyricsResult {
		val candidates = collectCandidates(artist, title, album, seconds)

		// Timings are only trusted when the playing track's duration is known and the hit's own
		// duration is close, so an album, single or extended version with the same name (and a
		// different intro) cannot drag the highlighted line out of sync.
		val synced = candidates
			.filter { it.isSynced() && fitsSynced(it, seconds) }
			.closestTo(seconds)
		if (synced != null) {
			val lines = LrcParser.parse(synced.text("syncedLyrics").orEmpty())
			if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
		}

		// No trustworthy timings: untimed lyrics are better than lines that are visibly out of sync.
		val plain = candidates.filter { it.text("plainLyrics") != null }.closestTo(seconds)
		plain?.text("plainLyrics")?.let { return LyricsResult.Plain(it.trim()) }

		return if (candidates.any { it.optBoolean("instrumental") }) {
			LyricsResult.Instrumental
		} else {
			LyricsResult.None
		}
	}

	/**
	 * Same-song hits for this track. The exact lookup is tried first; the search only runs when
	 * that hit cannot supply trustworthy timings, and it is also where the plain fallback lives.
	 */
	private fun collectCandidates(artist: String, title: String, album: String?, seconds: Int): List<JSONObject> {
		val cleanTitle = cleanTitle(title)
		val mainArtist = mainArtist(artist)

		val exact = getJson("$BaseUrl/get?${exactQuery(mainArtist, cleanTitle, album, seconds)}")
			?.let { body -> runCatching { JSONObject(body) }.getOrNull() }
			?.takeIf { hasText(it) && sameSong(it, artist, title) }

		if (exact != null && exact.isSynced() && fitsSynced(exact, seconds)) return listOf(exact)

		val hits = mutableListOf<JSONObject>()
		if (exact != null) hits += exact

		val searchJson = getJson(
			"$BaseUrl/search?artist_name=${enc(mainArtist)}&track_name=${enc(cleanTitle)}"
		) ?: return hits
		val array = runCatching { JSONArray(searchJson) }.getOrNull() ?: return hits
		for (index in 0 until array.length()) {
			val candidate = array.optJSONObject(index) ?: continue
			if (hasText(candidate) && sameSong(candidate, artist, title)) hits += candidate
		}
		return hits
	}

	private fun exactQuery(artist: String, title: String, album: String?, seconds: Int): String =
		buildString {
			append("artist_name=").append(enc(artist))
			append("&track_name=").append(enc(title))
			album?.takeIf { it.isNotBlank() }?.let { append("&album_name=").append(enc(it)) }
			if (seconds > 0) append("&duration=").append(seconds)
		}

	private fun JSONObject.isSynced(): Boolean = text("syncedLyrics") != null

	/**
	 * A lyric string, or null when the field is JSON null or blank. [JSONObject.optString] turns a
	 * JSON null into the text "null", which would otherwise be accepted as lyrics and shown on screen.
	 */
	private fun JSONObject.text(name: String): String? =
		if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

	/**
	 * Synced lines are only usable when the track's duration is known and the hit's is close: album,
	 * single and extended versions share a title but not their timings.
	 */
	private fun fitsSynced(candidate: JSONObject, seconds: Int): Boolean {
		if (seconds <= 0) return false
		val duration = candidate.optDouble("duration", 0.0)
		return duration > 0 && abs(duration - seconds) <= SyncedToleranceSec
	}

	/** Closest in duration; hits without a known duration sort last. */
	private fun List<JSONObject>.closestTo(seconds: Int): JSONObject? = minByOrNull { candidate ->
		val duration = candidate.optDouble("duration", 0.0)
		if (seconds > 0 && duration > 0) abs(duration - seconds) else Double.MAX_VALUE
	}

	private fun hasText(obj: JSONObject): Boolean =
		obj.isSynced() || obj.text("plainLyrics") != null || obj.optBoolean("instrumental")

	/** Same song by title and artist. Duration is judged per lyric type, not here. */
	private fun sameSong(obj: JSONObject, artist: String, title: String): Boolean {
		val candidateTitle = normalize(cleanTitle(obj.text("trackName").orEmpty()))
		if (candidateTitle.isEmpty() || candidateTitle != normalize(cleanTitle(title))) return false

		val candidateArtist = normalize(obj.text("artistName").orEmpty())
		val wantedArtist = normalize(mainArtist(artist))
		return wantedArtist.isNotEmpty() &&
			(candidateArtist.contains(wantedArtist) || wantedArtist.contains(candidateArtist))
	}

	/** Removes what streaming services add: "(feat. …)", "[Remastered]", "- Radio Edit", etc. */
	private fun cleanTitle(title: String): String = TrackMatch.cleanTitle(title)

	/** First artist only: "A, B", "A & B", "A feat. B" become "A". */
	private fun mainArtist(artist: String): String = TrackMatch.mainArtist(artist)

	private fun normalize(value: String): String = TrackMatch.normalize(value)

	private fun getJson(url: String): String? {
		val connection = (URL(url).openConnection() as HttpURLConnection).apply {
			requestMethod = "GET"
			setRequestProperty("User-Agent", UserAgent)
			setRequestProperty("Accept", "application/json")
			connectTimeout = 10_000
			readTimeout = 10_000
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