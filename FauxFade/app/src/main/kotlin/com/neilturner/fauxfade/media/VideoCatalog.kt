package com.neilturner.fauxfade.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.neilturner.fauxfade.R
import com.neilturner.fauxfade.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The top level of tvos26.json.
 *
 * Only the fields the app reads are declared; [Json] is configured to ignore the rest, which
 * is most of the file - categories, points of interest, subcategories and the like.
 */
@Serializable
data class AerialResponse(
	val assets: List<AerialAsset> = emptyList(),
	val version: Int = 0,
	val initialAssetCount: Int = 0,
)

/**
 * One aerial clip.
 *
 * The [Quality] variants are declared individually rather than kept in a map, so that a typo in
 * a key is a compile error rather than a silently empty variant at runtime.
 */
@Serializable
data class AerialAsset(
	val id: String = "",
	/** The human-readable name - "Korea and Japan Night". Used as the media id. */
	@SerialName("accessibilityLabel")
	val accessibilityLabel: String = "",
	@SerialName("url-1080-H264")
	val url1080H264: String? = null,
	@SerialName("url-1080-SDR")
	val url1080SDR: String? = null,
	@SerialName("url-1080-HDR")
	val url1080HDR: String? = null,
	@SerialName("url-4K-SDR")
	val url4KSDR: String? = null,
	@SerialName("url-4K-HDR")
	val url4KHDR: String? = null,
)

/**
 * Reads the bundled Apple Aerial catalogue and picks the ten clips for a run.
 *
 * The catalogue is 262KB of JSON shipped in res/raw, so it is read once on the IO dispatcher
 * and held; parsing it on the main thread would be visible as a stutter on the picker.
 */
object VideoCatalog {
	private const val CLIPS_PER_RUN = 10

	private val json = Json { ignoreUnknownKeys = true }

	/**
	 * The whole catalogue, parsed.
	 *
	 * Returns an empty list rather than throwing if the resource cannot be read or parsed: the
	 * picker must still come up, and a run with nothing in it is a better failure than an app
	 * that will not start.
	 */
	suspend fun load(context: Context): List<AerialAsset> =
		withContext(Dispatchers.IO) {
			try {
				val text =
					context.resources
						.openRawResource(R.raw.tvos26)
						.bufferedReader()
						.use { it.readText() }
				val assets = json.decodeFromString<AerialResponse>(text).assets
				Log.i(TAG, "catalog: ${assets.size} assets")
				assets
			} catch (e: Exception) {
				Log.e(TAG, "catalog: could not read tvos26.json", e)
				emptyList()
			}
		}

	/**
	 * Ten clips at [quality], re-rolled on every call.
	 *
	 * Shuffling the whole catalogue per call is what makes each quality come out with a
	 * different ten: the four calls do not share a list, so there is nothing to align.
	 *
	 * The caller is expected to keep the result for the whole run - the loop walks one list, so
	 * asking again partway through would swap clips out from under it.
	 *
	 * Assets missing the requested variant are dropped rather than substituted from another
	 * quality, since silently playing a different resolution than the one on screen would be
	 * worse than playing one fewer clip.
	 */
	fun pick(
		assets: List<AerialAsset>,
		quality: Quality,
	): List<VideoItem> =
		assets
			.shuffled()
			.mapNotNull { asset -> quality.urlFor(asset)?.let { VideoItem(asset.accessibilityLabel, reachable(it)) } }
			.take(CLIPS_PER_RUN)

	/**
	 * Drops an unplayable https URL down to http, for Apple's asset host.
	 *
	 * The catalogue lists every clip as https, but sylvan.apple.com serves a certificate issued
	 * by "Apple Server Authentication CA" - an internal CA that is not in the public trust
	 * store. Connecting over TLS fails with "Trust anchor for certification path not found" on a
	 * stock device, which leaves the player sitting at position 0 with nothing on screen and no
	 * error to explain it. The plain http endpoint is still served, so that is what the clips
	 * are fetched over, and network_security_config.xml permits cleartext for it.
	 *
	 * Scoped to the host rather than applied to every URL, so a catalogue entry from a source
	 * with a properly trusted certificate is left on https.
	 */
	private fun reachable(url: String): String = if (Uri.parse(url).host == INSECURE_HOST) url.replaceFirst(HTTPS, HTTP) else url
}

/** The one host in the catalogue whose certificate does not validate. */
private const val INSECURE_HOST = "sylvan.apple.com"

private const val HTTPS = "https://"
private const val HTTP = "http://"
