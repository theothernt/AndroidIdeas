package com.neilturner.musicfun.data.lyrics

/** A single synced line: [timeMs] from the start of the track, and its text. */
data class LrcLine(val timeMs: Long, val text: String)

sealed interface LyricsResult {
	data class Synced(val lines: List<LrcLine>) : LyricsResult
	data class Plain(val text: String) : LyricsResult
	data object Instrumental : LyricsResult
	data object None : LyricsResult
}