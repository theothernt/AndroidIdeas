package com.neilturner.musicfun.data.lyrics

import com.neilturner.musicfun.data.MusicMetadata
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
 * Looks up synced lyrics on LRCLIB (https://lrclib.net). Streaming metadata is messy (feat., remaster
 * suffixes, multiple artists), so the title/artist are cleaned and every search hit is verified before
 * it is accepted — otherwise a similarly named song gets shown instead of the one playing.
 */
object LyricsRepository {

	private const val BaseUrl = "https://lrclib.net/api"
	private const val UserAgent = "MusicFun/1.0 (Android)"

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
		android.util.Log.d("LyricsCheck", "fetch $artist - $title => $result")
		cache[key] = result
		result
	}

	private fun fetchNetwork(artist: String, title: String, album: String?, seconds: Int): LyricsResult {
		val cleanTitle = cleanTitle(title)
		val mainArtist = mainArtist(artist)

		val exactQuery = buildString {
			append("artist_name=").append(enc(mainArtist))
			append("&track_name=").append(enc(cleanTitle))
			album?.takeIf { it.isNotBlank() }?.let {
				append("&album_name=").append(enc(it))
			}
			if (seconds > 0) append("&duration=").append(seconds)
		}

		var best: JSONObject? = null
		getJson("$BaseUrl/get?$exactQuery")?.let { body ->
			val candidate = runCatching { JSONObject(body) }.getOrNull()
			if (candidate != null && hasText(candidate) && same(candidate, artist, title, seconds)) {
				best = candidate
			}
		}

		if (best == null) {
			val searchJson = getJson(
				"$BaseUrl/search?artist_name=${enc(mainArtist)}&track_name=${enc(cleanTitle)}"
			) ?: return LyricsResult.None
			val array = runCatching { JSONArray(searchJson) }.getOrNull()
				?: return LyricsResult.None

			for (index in 0 until array.length()) {
				val candidate = array.optJSONObject(index) ?: continue
				if (!hasText(candidate) || !same(candidate, artist, title, seconds)) continue
				val current = best
				if (current == null || better(candidate, current, seconds)) {
					best = candidate
				}
			}
		}

		val hit = best ?: return LyricsResult.None
		if (hit.optBoolean("instrumental")) return LyricsResult.Instrumental

		val synced = hit.optString("syncedLyrics")
		if (synced.isNotBlank()) {
			val lines = LrcParser.parse(synced)
			if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
		}

		val plain = hit.optString("plainLyrics")
		if (plain.isNotBlank()) return LyricsResult.Plain(plain)
		return LyricsResult.None
	}

	private fun hasText(obj: JSONObject): Boolean =
		obj.optString("syncedLyrics").isNotBlank() ||
			obj.optString("plainLyrics").isNotBlank() ||
			obj.optBoolean("instrumental")

	/** Same song: cleaned titles match, artists overlap, duration within a few seconds. */
	private fun same(obj: JSONObject, artist: String, title: String, seconds: Int): Boolean {
		val candidateTitle = normalize(cleanTitle(obj.optString("trackName")))
		val wantedTitle = normalize(cleanTitle(title))
		if (candidateTitle.isEmpty() || candidateTitle != wantedTitle) return false

		val candidateArtist = normalize(obj.optString("artistName"))
		val wantedArtist = normalize(mainArtist(artist))
		if (wantedArtist.isEmpty() || !(candidateArtist.contains(wantedArtist) ||
				wantedArtist.contains(candidateArtist))
		) {
			return false
		}

		val duration = obj.optDouble("duration", 0.0)
		return seconds <= 0 || duration <= 0 || abs(duration - seconds) <= 3
	}

	private fun better(obj: JSONObject, than: JSONObject, seconds: Int): Boolean {
		val durationA = if (seconds > 0) abs(obj.optDouble("duration", 1e9) - seconds) else 0.0
		val durationB = if (seconds > 0) abs(than.optDouble("duration", 1e9) - seconds) else 0.0
		val syncedA = obj.optString("syncedLyrics").isNotBlank()
		val syncedB = than.optString("syncedLyrics").isNotBlank()
		if (abs(durationA - durationB) <= 1) return syncedA && !syncedB
		return durationA < durationB
	}

	/** Removes what streaming services add: "(feat. …)", "[Remastered]", "- Radio Edit", etc. */
	private fun cleanTitle(title: String): String {
		val cleaned = title
			.replace(
				Regex("(?i)\\s*[(\\[](feat|ft|with)\\.?\\s[^)\\]]*[)\\]]"),
				"",
			)
			.replace(
				Regex(
					"(?i)\\s*[(\\[][^)\\]]*(remaster|explicit|clean|edit|version|mono|stereo|live|bonus|deluxe)[^)\\]]*[)\\]]"
				),
				"",
			)
			.replace(
				Regex("(?i)\\s+-\\s+.*(remaster|edit|version|mix|live|mono|stereo).*$"),
				"",
			)
			.trim()
		return cleaned.ifEmpty { title.trim() }
	}

	/** First artist only: "A, B", "A & B", "A feat. B" become "A". */
	private fun mainArtist(artist: String): String {
		val first = artist
			.split(Regex("(?i)\\s*(,|&| feat\\.? | ft\\.? | x | und | and |;)\\s*"))[0]
			.trim()
		return first.ifEmpty { artist.trim() }
	}

	private fun normalize(value: String): String =
		value.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

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