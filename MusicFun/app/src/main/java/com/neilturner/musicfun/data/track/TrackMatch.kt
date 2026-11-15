package com.neilturner.musicfun.data.track

import java.util.Locale

/**
 * Streaming services decorate titles and artists ("- 2007 Remaster", "feat. …", multiple artists).
 * Both the lyrics and the facts lookups need the same stripped-down version to match a catalog.
 */
object TrackMatch {

	/** Removes what streaming services add: "(feat. …)", "[Remastered]", "- Radio Edit", etc. */
	fun cleanTitle(title: String): String {
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
	fun mainArtist(artist: String): String {
		val first = artist
			.split(Regex("(?i)\\s*(,|&| feat\\.? | ft\\.? | x | und | and |;)\\s*"))[0]
			.trim()
		return first.ifEmpty { artist.trim() }
	}

	fun normalize(value: String): String =
		value.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
}
