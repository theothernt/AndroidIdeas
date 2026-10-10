package com.neilturner.playerexp.data.plex

import androidx.compose.runtime.Immutable
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
@Immutable
data class PlexLibraryItem(
    val ratingKey: String,
    val title: String?,
    val thumb: String,
    /** When Plex added it, which is what the shelves are ordered by. */
    val addedAt: Long?,
    /** The show's rating key for an episode card, so the episode can open its show page. Null for movies. */
    val showRatingKey: String? = null,
    /** The show's title for an episode card, used as the navigation title when the card plays it. */
    val showTitle: String? = null
)

/** A library and its contents, as the screen needs it. */
@Immutable
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

    /**
     * Newest additions first.
     *
     * Plex's own ordering for a library is whatever the section was configured with, and asking it to
     * sort with `sort=addedAt:desc` is not honoured everywhere, so the order is settled here where it
     * is the same on every server. Items Plex gave no date for keep the server's order at the end
     * rather than jumping to the top.
     */
    fun orderByRecentlyAdded(items: List<PlexLibraryItem>): List<PlexLibraryItem> =
        items.sortedWith(
            compareByDescending<PlexLibraryItem> { it.addedAt != null }
                .thenByDescending { it.addedAt ?: Long.MIN_VALUE }
        )

    private fun canonicalRank(title: String, canonicalNames: Set<String>): Int =
        if (normalise(title) in canonicalNames) 0 else 1

    private fun normalise(title: String): String = title.trim().lowercase()
}

/** Plex's own item type numbers, which the `type` query parameter wants as digits. */
const val PLEX_ITEM_TYPE_MOVIE = 1
const val PLEX_ITEM_TYPE_EPISODE = 4

/** The library agent's plugin id, which Plex requires on any scrobble or rating call. */
const val PLEX_LIBRARY_PLUGIN_ID = "com.plexapp.plugins.library"

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
    val year: Int? = null,
    val addedAt: Long? = null
)
