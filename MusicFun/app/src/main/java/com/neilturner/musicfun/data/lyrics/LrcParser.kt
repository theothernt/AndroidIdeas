package com.neilturner.musicfun.data.lyrics

/**
 * Parses LRCLIB's synced lyrics, which look like:
 * ```
 * [ar:Artist]
 * [00:12.34] First line
 * [00:15.00] Second line
 * ```
 * Multiple timestamps on one line and the `[offset:±ms]` tag are supported.
 */
object LrcParser {

	private val timestamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
	private val offsetTag = Regex("""\[offset:([+-]?\d+)]""")

	fun parse(input: String): List<LrcLine> {
		var offsetMs = 0L
		val lines = mutableListOf<LrcLine>()

		for (raw in input.lines()) {
			val line = raw.trim()

			offsetTag.find(line)?.let {
				offsetMs = it.groupValues[1].toLong()
				continue
			}

			val stamps = timestamp.findAll(line).toList()
			if (stamps.isEmpty()) continue

			val text = line.substring(stamps.last().range.last + 1)
				.replace(Regex("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>"), "")
				.replace(Regex("\\s+"), " ")
				.trim()
			// Timestamped blank lines (common at instrumental breaks) would otherwise become an
			// empty "current" line in the karaoke panel.
			if (text.isEmpty()) continue
			for (stamp in stamps) {
				val minutes = stamp.groupValues[1].toLong()
				val seconds = stamp.groupValues[2].toLong()
				val fraction = stamp.groupValues[3]
				val fractionMs = when (fraction.length) {
					0 -> 0L
					1 -> fraction.toLong() * 100L
					2 -> fraction.toLong() * 10L
					else -> fraction.take(3).toLong()
				}
				lines += LrcLine(
					timeMs = (minutes * 60_000 + seconds * 1_000 + fractionMs + offsetMs)
						.coerceAtLeast(0),
					text = text,
				)
			}
		}

		return lines.sortedBy { it.timeMs }
	}
}