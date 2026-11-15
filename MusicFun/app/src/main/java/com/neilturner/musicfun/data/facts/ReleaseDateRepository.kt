package com.neilturner.musicfun.data.facts

import com.neilturner.musicfun.data.MusicMetadata
import com.neilturner.musicfun.data.track.TrackMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A calendar date at whatever precision MusicBrainz has; month and day default to 1. */
data class ReleaseDate(val year: Int, val month: Int = 1, val day: Int = 1)

/** What the catalogs know about the track: when it came out, and the genre Apple files it under. */
data class SongFacts(val releaseDate: ReleaseDate? = null, val genre: String? = null)

/**
 * Finds when a track was first released, and the genre Apple files it under.
 *
 * Apple's catalog is asked first: its dates survive remasters and compilations ("Greatest Hits" still
 * carries the original year), which MusicBrainz's release data often misses. MusicBrainz is the
 * fallback when Apple has no match, and its recording/release-group dates are used the same way.
 * The genre rides along in the Apple response this already fetches, so it costs no extra request;
 * MusicBrainz is never asked for one.
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

	/** Both catalogs grow fields and null optional ones, so unknown keys and nulls are tolerated. */
	private val json = Json {
		ignoreUnknownKeys = true
		coerceInputValues = true
		isLenient = true
	}

	/** "Greatest Hits", "The Collection", "Best Of …": the release group is not the song's date. */
	private val CompilationWords = Regex(
		"(?i)\\b(greatest hits|best of|the best|very best|essential|collection|anthology|hits|compilation)\\b"
	)

	private val cache = mutableMapOf<String, SongFacts?>()
	private val rateLock = Mutex()
	private var lastRequestAt = 0L

	suspend fun fetch(metadata: MusicMetadata): SongFacts? = withContext(Dispatchers.IO) {
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

	private suspend fun fetchNetwork(artist: String, title: String, album: String): SongFacts {
		val apple = itunesFacts(artist, title)
		// MusicBrainz can only improve the date; it would need another request for a genre.
		if (apple.releaseDate != null) return apple
		return apple.copy(releaseDate = musicBrainzDate(artist, title, album))
	}

	/**
	 * Apple keeps the original date on remasters and hits collections, where a release-based source
	 * would report the reissue. Its search returns one row per album/single the track appears on, so
	 * the earliest match is the song's first release; live versions and remixes are always later.
	 *
	 * The genre comes from that same row, so the fact describes the release the date came from; when
	 * no row has a usable date, the first matched row's genre is still worth showing.
	 */
	private suspend fun itunesFacts(artist: String, title: String): SongFacts {
		awaitRateSlot()

		val term = TrackMatch.mainArtist(artist) + " " + TrackMatch.cleanTitle(title)
		val body = getJson("$ItunesUrl?term=${enc(term)}&entity=song&media=music&limit=25")
			?: return SongFacts()
		val response = runCatching { json.decodeFromString<ItunesSearch>(body) }.getOrNull()
			?: return SongFacts()

		val wantedArtist = TrackMatch.normalize(TrackMatch.mainArtist(artist))
		var earliest: ReleaseDate? = null
		var earliestGenre: String? = null
		var anyGenre: String? = null
		for (row in response.results) {
			val rowArtist = TrackMatch.normalize(row.artistName)
			if (!rowArtist.contains(wantedArtist)) continue
			if (!titlesMatch(title, row.trackName)) continue
			val genre = row.primaryGenreName.takeIf { it.isNotBlank() }
			if (anyGenre == null) anyGenre = genre
			val date = parseDate(row.releaseDate?.take(10).orEmpty()) ?: continue
			if (earliest == null || date.sortKey() < earliest.sortKey()) {
				earliest = date
				earliestGenre = genre
			}
		}
		return SongFacts(earliest, earliestGenre ?: anyGenre)
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
		val body = getJson("$BaseUrl/recording/?query=${enc(query)}&fmt=json&limit=10")
			?: return null
		val response = runCatching { json.decodeFromString<RecordingSearch>(body) }.getOrNull()
			?: return null

		val wantedTitle = TrackMatch.normalize(cleanedTitle)
		val wantedArtist = TrackMatch.normalize(mainArtist)
		var earliest: ReleaseDate? = null

		for (recording in response.recordings) {
			val candidateTitle = TrackMatch.normalize(TrackMatch.cleanTitle(recording.title))
			if (candidateTitle != wantedTitle) continue
			if (!hasArtist(recording, wantedArtist)) continue

			for (release in recording.releases) {
				earliest = earlierOf(earliest, parseDate(release.date))
				earliest = earlierOf(earliest, parseDate(release.releaseGroup?.firstReleaseDate.orEmpty()))
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
		val body = getJson("$BaseUrl/release-group/?query=${enc(query)}&fmt=json&limit=10")
			?: return null
		val response = runCatching { json.decodeFromString<ReleaseGroupSearch>(body) }.getOrNull()
			?: return null

		val wantedAlbum = TrackMatch.normalize(cleanedAlbum)
		val wantedArtist = TrackMatch.normalize(mainArtist)
		var earliest: ReleaseDate? = null
		for (group in response.releaseGroups) {
			if (TrackMatch.normalize(group.title) != wantedAlbum) continue
			if (!hasArtist(group.artistCredit, wantedArtist)) continue
			earliest = earlierOf(earliest, parseDate(group.firstReleaseDate))
		}
		return earliest
	}

	private fun hasArtist(recording: MbRecording, wantedArtist: String): Boolean =
		hasArtist(recording.artistCredit, wantedArtist)

	private fun hasArtist(credit: List<MbArtistCredit>, wantedArtist: String): Boolean {
		if (wantedArtist.isEmpty()) return false
		return credit.any { entry ->
			val name = TrackMatch.normalize(entry.name)
			name.isNotEmpty() && (name.contains(wantedArtist) || wantedArtist.contains(name))
		}
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

/** Apple's search response: one row per album or single the track appears on. */
@Serializable
private data class ItunesSearch(val results: List<ItunesSong> = emptyList())

@Serializable
private data class ItunesSong(
	val artistName: String = "",
	val trackName: String = "",
	/** Missing on some rows; only the date part is used ("2019-11-29T08:00:00Z"). */
	val releaseDate: String? = null,
	/** Apple's own classification, e.g. "Electronic"; blank on a few rows. */
	val primaryGenreName: String = "",
)

@Serializable
private data class RecordingSearch(val recordings: List<MbRecording> = emptyList())

@Serializable
private data class MbRecording(
	val title: String = "",
	@SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
	val releases: List<MbRelease> = emptyList(),
)

@Serializable
private data class MbRelease(
	val date: String = "",
	@SerialName("release-group") val releaseGroup: MbReleaseGroup? = null,
)

@Serializable
private data class MbReleaseGroup(
	val title: String = "",
	@SerialName("first-release-date") val firstReleaseDate: String = "",
	/** Present on release-group search rows; the nested one inside a release has no credits. */
	@SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
)

@Serializable
private data class ReleaseGroupSearch(
	@SerialName("release-groups") val releaseGroups: List<MbReleaseGroup> = emptyList(),
)

@Serializable
private data class MbArtistCredit(val name: String = "")
