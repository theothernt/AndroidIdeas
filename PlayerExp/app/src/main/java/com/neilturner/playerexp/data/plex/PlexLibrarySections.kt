package com.neilturner.playerexp.data.plex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A Plex library as the server lists it.
 *
 * A server can hold many libraries of the same kind, and the ones worth showing are not always the
 * ones a type check alone would pick: a library called "Alt. TV" is the same kind of library as
 * "TV Shows" and would pass any rule that only looked at [type].
 */
/** One show or movie in a library, with its poster already sized for the shelf it will be drawn on. */
data class PlexLibraryItem(
    val ratingKey: String,
    val title: String?,
    val thumb: String
)

/** A library and its contents, as the screen needs it. */
data class PlexLibraryShelf(
    val sectionKey: String,
    val sectionTitle: String,
    val items: List<PlexLibraryItem>
)

/**
 * Chooses which library stands in for "TV Shows" and which for "Movies".
 *
 * Plex servers often carry more than one library of each kind, and the alternates are usually there
 * for a second home or a test. Nothing in the API marks one as primary, so the rule is by name:
 * libraries that announce themselves as alternates are skipped, a library carrying a canonical name
 * wins, and the server's own order breaks any remaining tie, which is the order Plex reports them in
 * and is stable across requests.
 */
object PlexLibrarySections {

    const val TYPE_SHOW = "show"
    const val TYPE_MOVIE = "movie"

    private val TV_NAMES = setOf("tv", "tv shows", "tvshow", "tv shows", "shows", "series")
    private val MOVIE_NAMES = setOf("movie", "movies", "film", "films", "cinema")

    fun pickTvShows(sections: List<PlexSectionDirectory>): PlexSectionDirectory? = pick(sections, TYPE_SHOW, TV_NAMES)

    fun pickMovies(sections: List<PlexSectionDirectory>): PlexSectionDirectory? = pick(sections, TYPE_MOVIE, MOVIE_NAMES)

    /**
     * "alt", "alt." or "alternate" as a word of its own, or a "test" library. Matching whole words
     * matters: a title that merely starts with the same letters, such as "Alternative Rock", is a
     * real library and not a stand-in.
     */
    private val ALTERNATE_MARKER = Regex("\\balt\\.?\\b|\\balternate\\b|\\btest\\b", RegexOption.IGNORE_CASE)

    /** Names that mark a library as a stand-in rather than the main one. */
    fun looksAlternate(title: String): Boolean = ALTERNATE_MARKER.containsMatchIn(title.trim())

    private fun pick(
        sections: List<PlexSectionDirectory>,
        type: String,
        canonicalNames: Set<String>
    ): PlexSectionDirectory? =
        sections
            .filter { it.type.equals(type, ignoreCase = true) }
            .filter { it.key.isNotBlank() }
            .filterNot { looksAlternate(it.title.orEmpty()) }
            .minByOrNull { canonicalRank(it.title.orEmpty(), canonicalNames) }

    private fun canonicalRank(title: String, canonicalNames: Set<String>): Int =
        if (normalise(title) in canonicalNames) 0 else 1

    private fun normalise(title: String): String = title.trim().lowercase()
}

@Serializable
internal data class PlexSectionItemsResponse(
    @SerialName("MediaContainer") val mediaContainer: PlexSectionItemsContainer? = null
)

@Serializable
internal data class PlexSectionItemsContainer(
    @SerialName("Metadata") val metadata: List<PlexSectionItem> = emptyList(),
    val totalSize: Int? = null,
    val size: Int? = null
)

@Serializable
internal data class PlexSectionItem(
    val ratingKey: String? = null,
    val title: String? = null,
    val type: String? = null,
    val thumb: String? = null,
    val year: Int? = null
)
