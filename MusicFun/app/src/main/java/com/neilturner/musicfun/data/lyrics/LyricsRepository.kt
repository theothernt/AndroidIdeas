package com.neilturner.musicfun.data.lyrics

import com.neilturner.musicfun.data.MusicMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Looks up synced lyrics on LRCLIB (https://lrclib.net). Results are cached per track for the
 * lifetime of the process.
 */
object LyricsRepository {

	private const val BaseUrl = "https://lrclib.net/api"
	private const val UserAgent = "MusicFun/1.0 (Android)"

	private val cache = mutableMapOf<String, LyricsResult>()

	suspend fun fetch(metadata: MusicMetadata): LyricsResult = withContext(Dispatchers.IO) {
		val key = listOf(
			metadata.artist,
			metadata.title,
			metadata.album,
			metadata.durationMs,
		).joinToString("|")
		cache[key]?.let { return@withContext it }

		val result = runCatching { fetchNetwork(metadata) }
			.getOrElse { LyricsResult.None }
		cache[key] = result
		result
	}

	private fun fetchNetwork(metadata: MusicMetadata): LyricsResult {
		val artist = metadata.artist?.trim().orEmpty()
		val title = metadata.title?.trim().orEmpty()
		if (artist.isEmpty() || title.isEmpty()) return LyricsResult.None

		val query = buildString {
			append("artist_name=").append(enc(artist))
			append("&track_name=").append(enc(title))
			metadata.album?.takeIf { it.isNotBlank() }?.let {
				append("&album_name=").append(enc(it))
			}
			metadata.durationMs?.takeIf { it > 0 }?.let {
				append("&duration=").append(it / 1000)
			}
		}
		getJson("$BaseUrl/get?$query")?.let { parse(it) }?.let { return it }

		// The exact lookup needs a close metadata match; fall back to a search and prefer a result
		// with synced lyrics and a similar duration.
		val searchJson = getJson("$BaseUrl/search?q=${enc("$artist $title")}")
			?: return LyricsResult.None
		val array = runCatching { JSONArray(searchJson) }.getOrNull()
			?: return LyricsResult.None

		val durationSec = metadata.durationMs?.div(1000)
		var firstSynced: LyricsResult? = null
		for (index in 0 until array.length()) {
			val item = array.optJSONObject(index) ?: continue
			val synced = item.optString("syncedLyrics")
			if (synced.isNullOrBlank()) continue

			val result = LyricsResult.Synced(LrcParser.parse(synced))
			if (firstSynced == null) firstSynced = result

			val itemDuration = item.optDouble("duration", 0.0)
			if (durationSec == null || abs(itemDuration - durationSec) <= 10) {
				return result
			}
		}
		return firstSynced ?: LyricsResult.None
	}

	private fun parse(json: String): LyricsResult? {
		val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null

		val synced = obj.optString("syncedLyrics")
		if (!synced.isNullOrBlank()) {
			val lines = LrcParser.parse(synced)
			if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
		}

		val plain = obj.optString("plainLyrics")
		if (!plain.isNullOrBlank()) return LyricsResult.Plain(plain)

		if (obj.optBoolean("instrumental")) return LyricsResult.Instrumental
		return LyricsResult.None
	}

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