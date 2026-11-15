package com.neilturner.musicfun.data.lyrics

import com.neilturner.musicfun.data.MusicMetadata
import com.neilturner.musicfun.data.track.TrackMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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

	/** LRCLIB adds fields over time and omits others per record, so unknown keys and nulls are fine. */
	private val json = Json {
		ignoreUnknownKeys = true
		coerceInputValues = true
		isLenient = true
	}

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
			.filter { it.hasSynced() && fitsSynced(it, seconds) }
			.closestTo(seconds)
		if (synced != null) {
			val lines = LrcParser.parse(synced.syncedLyrics.orEmpty())
			if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
		}

		// No trustworthy timings: untimed lyrics are better than lines that are visibly out of sync.
		val plain = candidates.filter { it.hasPlain() }.closestTo(seconds)
		plain?.plainLyrics?.let { return LyricsResult.Plain(it.trim()) }

		return if (candidates.any { it.instrumental }) {
			LyricsResult.Instrumental
		} else {
			LyricsResult.None
		}
	}

	/**
	 * Same-song hits for this track. The exact lookup is tried first; the search only runs when
	 * that hit cannot supply trustworthy timings, and it is also where the plain fallback lives.
	 */
	private fun collectCandidates(artist: String, title: String, album: String?, seconds: Int): List<LyricHit> {
		val cleanTitle = cleanTitle(title)
		val mainArtist = mainArtist(artist)

		val exact = getJson("$BaseUrl/get?${exactQuery(mainArtist, cleanTitle, album, seconds)}")
			?.let { body -> runCatching { json.decodeFromString<LyricHit>(body) }.getOrNull() }
			?.takeIf { it.hasText() && sameSong(it, artist, title) }

		if (exact != null && exact.hasSynced() && fitsSynced(exact, seconds)) return listOf(exact)

		val hits = mutableListOf<LyricHit>()
		if (exact != null) hits += exact

		val searchJson = getJson(
			"$BaseUrl/search?artist_name=${enc(mainArtist)}&track_name=${enc(cleanTitle)}"
		) ?: return hits
		val results = runCatching { json.decodeFromString<List<LyricHit>>(searchJson) }.getOrNull()
			?: return hits
		for (candidate in results) {
			if (candidate.hasText() && sameSong(candidate, artist, title)) hits += candidate
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

	private fun LyricHit.hasSynced(): Boolean = !syncedLyrics.isNullOrBlank()

	private fun LyricHit.hasPlain(): Boolean = !plainLyrics.isNullOrBlank()

	private fun LyricHit.hasText(): Boolean = hasSynced() || hasPlain() || instrumental

	/**
	 * Synced lines are only usable when the track's duration is known and the hit's is close: album,
	 * single and extended versions share a title but not their timings.
	 */
	private fun fitsSynced(hit: LyricHit, seconds: Int): Boolean {
		if (seconds <= 0) return false
		val duration = hit.duration ?: return false
		return duration > 0 && abs(duration - seconds) <= SyncedToleranceSec
	}

	/** Closest in duration; hits without a known duration sort last. */
	private fun List<LyricHit>.closestTo(seconds: Int): LyricHit? = minByOrNull { hit ->
		val duration = hit.duration ?: 0.0
		if (seconds > 0 && duration > 0) abs(duration - seconds) else Double.MAX_VALUE
	}

	/** Same song by title and artist. Duration is judged per lyric type, not here. */
	private fun sameSong(hit: LyricHit, artist: String, title: String): Boolean {
		val candidateTitle = normalize(cleanTitle(hit.trackName))
		if (candidateTitle.isEmpty() || candidateTitle != normalize(cleanTitle(title))) return false

		val candidateArtist = normalize(hit.artistName)
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

/**
 * One LRCLIB lyric record. Everything has a default because the API nulls or omits fields per
 * record: an instrumental hit carries no lyric text, and a search row may have no duration.
 */
@Serializable
private data class LyricHit(
	val trackName: String = "",
	val artistName: String = "",
	val syncedLyrics: String? = null,
	val plainLyrics: String? = null,
	val duration: Double? = null,
	val instrumental: Boolean = false,
)
