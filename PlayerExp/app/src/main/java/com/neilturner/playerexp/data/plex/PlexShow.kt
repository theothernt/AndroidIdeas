package com.neilturner.playerexp.data.plex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexShowDetailsResponse(
    @SerialName("MediaContainer") val mediaContainer: PlexShowDetailsContainer? = null
)

@Serializable
data class PlexShowDetailsContainer(
    @SerialName("Metadata") val metadata: List<PlexShowMetadata>? = null
)

/**
 * A TV show's own metadata, as Plex returns it from `/library/metadata/{ratingKey}`.
 *
 * `childCount` is the number of seasons the show carries and `leafCount` is its episode count
 * (both include or exclude season zero the same way Plex counts it), so they are the figures the
 * screen reports without having to count the episodes itself. `viewedLeafCount` is how many of
 * those the account has finished.
 */
@Serializable
data class PlexShowMetadata(
    val ratingKey: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val year: Int? = null,
    val thumb: String? = null,
    /** Wide backdrop, the one drawn behind the whole screen rather than in a poster slot. */
    val art: String? = null,
    val contentRating: String? = null,
    val audienceRating: Float? = null,
    val audienceRatingImage: String? = null,
    val rating: Float? = null,
    val ratingImage: String? = null,
    val tagline: String? = null,
    /** Number of direct children: the seasons in the library. */
    val childCount: Int? = null,
    /** Number of leaf descendants: the episodes in the show. */
    val leafCount: Int? = null,
    /** How many of those episodes the account has watched. */
    val viewedLeafCount: Int? = null,
    val genre: List<PlexGenre>? = null
)

@Serializable
data class PlexGenre(val tag: String? = null)

@Serializable
data class PlexShowEpisodesResponse(
    @SerialName("MediaContainer") val mediaContainer: PlexShowEpisodesContainer? = null
)

@Serializable
data class PlexShowEpisodesContainer(
    @SerialName("Metadata") val metadata: List<PlexShowEpisodeMetadata>? = null,
    val totalSize: Int? = null
)

/**
 * One episode from `/library/metadata/{showKey}/allLeaves`.
 *
 * This is the whole shape the screen draws from, so the still, the air date and the rating all
 * arrive with the list rather than costing a request each. `parentTitle` is the season's own name
 * — Plex files a season as "Season 3" unless it has a real one — which is what the season chip and
 * the hero subtitle both show.
 */
@Serializable
data class PlexShowEpisodeMetadata(
    val ratingKey: String? = null,
    val title: String? = null,
    val originalTitle: String? = null,
    val summary: String? = null,
    /** Season number (parentIndex) for an episode. Null marks a special. */
    val parentIndex: Int? = null,
    /** Episode number within its season (index). */
    val index: Int? = null,
    val parentTitle: String? = null,
    val parentRatingKey: String? = null,
    /** The episode's own landscape still, the frame the carousel card is drawn from. */
    val thumb: String? = null,
    val duration: Long? = null,
    val viewOffset: Long? = null,
    val viewCount: Int? = null,
    val originallyAvailableAt: String? = null,
    val contentRating: String? = null,
    val audienceRating: Float? = null,
    /** Which service the rating came from, as a Plex URI: `themoviedb://image.rating`. */
    val audienceRatingImage: String? = null,
    val rating: Float? = null,
    val ratingImage: String? = null
)

/**
 * The whole show, ready for the screen: the summary plus every episode grouped into [seasons].
 * Episodes are fetched for the whole show up front and split here so the screen only makes one
 * request per visit, the same shape On Deck fetches in.
 */
data class PlexShow(
    val ratingKey: String,
    val title: String?,
    val summary: String?,
    val year: Int?,
    val posterUrl: String?,
    val seasonCount: Int?,
    val episodeCount: Int?,
    val seasons: List<PlexShowSeason> = emptyList(),
    /** Wide backdrop behind the screen. Falls back to the poster when a show has no art. */
    val artUrl: String? = null,
    val contentRating: String? = null,
    val rating: Float? = null,
    val tagline: String? = null,
    val watchedEpisodeCount: Int? = null
)

data class PlexShowSeason(
    val seasonNumber: Int?,
    val episodes: List<PlexShowEpisode>,
    /** Plex's own name for the season, "A Quiet Life" where it has one. */
    val plexTitle: String? = null
) {
    /** "Season N", or "Specials" for everything Plex filed without one. */
    val title: String
        get() = seasonNumber?.let { "Season $it" } ?: "Specials"

    /**
     * What the season is called on screen: the name Plex gives it when that is more than the bare
     * number it would otherwise show.
     */
    val displayTitle: String
        get() = plexTitle?.takeIf { it.isNotBlank() && it != title } ?: title
}

data class PlexShowEpisode(
    val ratingKey: String,
    val title: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val duration: Long?,
    val viewOffset: Long?,
    /** The season's own name, carried so the chip and hero can show it without another request. */
    val seasonTitle: String? = null,
    val thumbUrl: String? = null,
    val summary: String? = null,
    /** Air date as Plex reports it, `2026-01-05`. */
    val airDate: String? = null,
    val contentRating: String? = null,
    val rating: Float? = null,
    val ratingSource: String? = null,
    val isWatched: Boolean = false
) {
    val progressFraction: Float
        get() = if (duration != null && duration > 0L && viewOffset != null && viewOffset > 0L) {
            (viewOffset.toFloat() / duration).coerceIn(0f, 1f)
        } else 0f

    val hasProgress: Boolean get() = progressFraction > 0f
}

/**
 * Episode title for the player screen: a show episode plays as "Show — Episode",
 * the same shape [OnDeckItem.displayTitle] uses on the row. The show title is supplied by
 * whoever holds the screen, since a per-episode fetch does not always carry it.
 */
fun PlexShowEpisode.displayTitle(showTitle: String?): String =
    listOfNotNull(showTitle, title).joinToString(" — ")

/**
 * Splits a flat episode list into seasons for the screen.
 *
 * Episodes are ordered season-then-episode and grouped by season number; anything Plex filed
 * without one (specials) lands in a single "Specials" season kept last, so the list reads as a
 * catalogue rather than jumping to the front. Items with no rating key are dropped, since there
 * is nothing to play behind them.
 */
fun groupEpisodesBySeason(episodes: List<PlexShowEpisode>): List<PlexShowSeason> =
    episodes
        .filter { it.ratingKey.isNotBlank() }
        .sortedWith(
            compareBy(
                { it.seasonNumber ?: Int.MAX_VALUE },
                { it.episodeNumber ?: Int.MAX_VALUE }
            )
        )
        .groupBy { it.seasonNumber }
        .entries
        .sortedBy { (seasonNumber, _) -> seasonNumber ?: Int.MAX_VALUE }
        .map { (seasonNumber, episodesInSeason) ->
            PlexShowSeason(
                seasonNumber = seasonNumber,
                episodes = episodesInSeason,
                plexTitle = episodesInSeason.firstOrNull { !it.seasonTitle.isNullOrBlank() }?.seasonTitle
            )
        }

/**
 * The label the rating is filed under, taken from the Plex URI in `audienceRatingImage`
 * (`themoviedb://image.rating` reads as "TMDB"). Null when the episode carries no rating.
 */
fun PlexShowEpisode.ratingLabel(): String? {
    if (rating == null) return null
    val source = ratingSource.orEmpty()
    return when {
        source.contains("imdb", ignoreCase = true) -> "IMDb"
        source.contains("thetvdb", ignoreCase = true) || source.contains("tvdb", ignoreCase = true) -> "TVDB"
        source.contains("themoviedb", ignoreCase = true) || source.contains("tmdb", ignoreCase = true) -> "TMDB"
        source.contains("rottentomatoes", ignoreCase = true) -> "RT"
        else -> null
    }
}