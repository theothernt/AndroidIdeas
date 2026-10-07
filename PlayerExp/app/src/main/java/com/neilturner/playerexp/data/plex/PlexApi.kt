package com.neilturner.playerexp.data.plex

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.client.request.forms.FormDataContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.CancellationException

// Matches the lenient, ignore-unknown parser the rest of the Plex calls expect: Plex responses
// carry plenty of fields this screen never reads.
val plexJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Reduces an item key to the bare rating key every request here builds its URL from.
 *
 * Plex reports the same item's key in two shapes: a bare rating key (`40543`) in most responses,
 * but a full library path (`/library/metadata/40543`) in others — `grandparentKey` on a recently
 * added episode is the path form. Every endpoint here appends the key to `/library/metadata/`,
 * so a path handed straight to it produces a doubled URL that Plex answers with a 404 HTML page
 * rather than JSON. Taking the last segment covers both shapes.
 */
internal fun plexRatingKey(key: String?): String? {
    val trimmed = key?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    // A path that stops at a slash names a collection, not an item, so there is no key to take.
    if (trimmed.endsWith('/')) return null
    return trimmed.substringAfterLast('/').takeIf { it.isNotBlank() }
}

@Serializable
data class PlexPin(
    val id: Long,
    val code: String,
    @SerialName("authToken") val authToken: String? = null
)

@Serializable
data class PlexConnection(
    val uri: String? = null,
    val address: String? = null,
    val port: Int? = null,
    val protocol: String? = null,
    val local: Boolean = false,
    val relay: Boolean = false
)

@Serializable
data class PlexResource(
    val name: String,
    val product: String? = null,
    val provides: String? = null,
    val connections: List<PlexConnection>? = null,
    val accessToken: String? = null
)

data class PlexServerInfo(
    val name: String,
    val uri: String?,
    val accessToken: String? = null
)

@Serializable
data class PlexSectionsResponse(
    @SerialName("MediaContainer") val mediaContainer: PlexSectionsContainer? = null
)

@Serializable
data class PlexSectionsContainer(
    @SerialName("Directory") val directory: List<PlexSectionDirectory>? = null
)

@Serializable
data class PlexSectionDirectory(
    val key: String,
    val type: String,
    val title: String? = null
)

@Serializable
data class PlexMediaContainerResponse(
    @SerialName("MediaContainer") val mediaContainer: PlexEpisodesContainer? = null
)

private const val EPISODE_TYPE = "episode"

/** One Continue Watching item. [thumb] is an absolute URL the UI can load directly. */
data class OnDeckItem(
    val ratingKey: String,
    val title: String?,
    val grandparentTitle: String?,
    val thumb: String,
    val viewOffset: Long,
    val duration: Long,
    val type: String?
) {
    val progressFraction: Float
        get() = if (duration > 0L) (viewOffset.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val isEpisode: Boolean get() = type == EPISODE_TYPE
}

/** Title to show on a card: episodes read better as "Show — Episode". */
val OnDeckItem.displayTitle: String
    get() = if (isEpisode) {
        listOfNotNull(grandparentTitle, title).joinToString(" — ")
    } else {
        title ?: grandparentTitle.orEmpty()
    }

@Serializable
data class PlexEpisodesContainer(
    @SerialName("Metadata") val metadata: List<PlexEpisodeMetadata>? = null,
    val totalSize: Int? = null
)

@Serializable
data class PlexEpisodeMetadata(
    val ratingKey: String? = null,
    val title: String? = null,
    val grandparentTitle: String? = null,
    val grandparentKey: String? = null,
    val parentIndex: Int? = null,
    val index: Int? = null,
    val thumb: String? = null,
    val grandparentThumb: String? = null,
    val addedAt: Long? = null,
    @SerialName("Media") val media: List<PlexMediaItem>? = null
)

@Serializable
private data class PlexOnDeckResponse(
    @SerialName("MediaContainer") val mediaContainer: PlexOnDeckContainer? = null
)

@Serializable
private data class PlexOnDeckContainer(
    @SerialName("Metadata") val metadata: List<PlexOnDeckMetadata>? = null,
    val size: Int? = null,
    val totalSize: Int? = null
)

@Serializable
private data class PlexOnDeckMetadata(
    val ratingKey: String? = null,
    val title: String? = null,
    val type: String? = null,
    val thumb: String? = null,
    val grandparentTitle: String? = null,
    val parentThumb: String? = null,
    val grandparentThumb: String? = null,
    val viewOffset: Long? = null,
    val duration: Long? = null,
    @SerialName("UserState") val userState: PlexOnDeckUserState? = null
)

/** Plex reports per-user progress here on current servers; older ones only set the metadata fields. */
@Serializable
private data class PlexOnDeckUserState(
    val viewOffset: Long? = null,
    val duration: Long? = null,
    val viewCount: Int? = null,
    val played: Boolean? = null
)

@Serializable
data class PlexMediaItem(
    @SerialName("Part") val part: List<PlexMediaPart>? = null
)

@Serializable
data class PlexMediaPart(
    val id: Long? = null,
    val key: String? = null
)

data class PlexEpisode(
    val ratingKey: String?,
    val showTitle: String?,
    val episodeTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val partKey: String
)

sealed interface PlexPlaybackPlan {
    val url: String
    val decisionText: String?
    val playbackStatus: PlexPlaybackStatus

    data class DirectPlay(
        override val url: String,
        override val decisionText: String?,
        override val playbackStatus: PlexPlaybackStatus
    ) : PlexPlaybackPlan

    data class HlsTranscode(
        override val url: String,
        override val decisionText: String?,
        override val playbackStatus: PlexPlaybackStatus
    ) : PlexPlaybackPlan
}

data class PlexPlaybackStatus(
    val video: PlexStreamPlayback,
    val audio: PlexStreamPlayback
)

data class PlexStreamPlayback(
    val mode: PlexStreamMode,
    val codec: String? = null
)

enum class PlexStreamMode(val displayName: String) {
    Direct("Direct"),
    Transcoded("Transcoded")
}

@Serializable
private data class PlexPlaybackDecisionResponse(
    @SerialName("MediaContainer") val mediaContainer: PlexPlaybackDecisionContainer? = null
)

@Serializable
private data class PlexPlaybackDecisionContainer(
    val generalDecisionCode: Int? = null,
    val generalDecisionText: String? = null,
    val mdeDecisionCode: Int? = null,
    val mdeDecisionText: String? = null,
    val directPlayDecisionCode: Int? = null,
    val directPlayDecisionText: String? = null,
    @SerialName("Metadata") val metadata: List<PlexPlaybackDecisionMetadata>? = null
)

@Serializable
private data class PlexPlaybackDecisionMetadata(
    @SerialName("Media") val media: List<PlexPlaybackDecisionMedia>? = null
)

@Serializable
private data class PlexPlaybackDecisionMedia(
    @SerialName("Part") val part: List<PlexPlaybackDecisionPart>? = null
)

@Serializable
private data class PlexPlaybackDecisionPart(
    val decision: String? = null,
    @SerialName("Stream") val stream: List<PlexPlaybackDecisionStream>? = null
)

@Serializable
private data class PlexPlaybackDecisionStream(
    val streamType: Int? = null,
    val decision: String? = null,
    val codec: String? = null
)

private data class PlaybackDecisionResult(
    val response: PlexPlaybackDecisionContainer,
    val generalCode: Int?,
    val directPlayCode: Int?,
    val decisionText: String?
)

class PlexApi(private val clientIdentifier: String) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(WebSockets) {
            pingIntervalMillis = 20_000L
        }
    }

    suspend fun createPin(): PlexPin = apiCall("Create PIN") { client.post("https://plex.tv/api/v2/pins") {
        plexHeaders()
    } }.body()

    suspend fun pin(pinId: Long): PlexPin = apiCall("Get PIN") { client.get("https://plex.tv/api/v2/pins/$pinId") {
        plexHeaders()
    } }.body()

    suspend fun serverInfo(accountToken: String): PlexServerInfo? {
        val plexJson = Json { ignoreUnknownKeys = true; isLenient = true }
        Log.d("PlexApi", "serverInfo() called, token present: ${accountToken.isNotBlank()}")

        // Primary: /api/v2/resources
        val resourcesFromPrimary = try {
            val response = apiCall("List Plex resources (v2)") { client.get("https://plex.tv/api/v2/resources") {
                parameter("includeHttps", 1)
                plexHeaders(accountToken)
            } }
            val contentType = response.headers[HttpHeaders.ContentType] ?: "unknown"
            val bodyString = response.body<String>()
            Log.d("PlexApi", "Resources v2 Content-Type: $contentType")
            Log.d("PlexApi", "Resources v2 body: ${bodyString.take(1000)}")
            val devices = plexJson.decodeFromString<List<PlexResource>>(bodyString)
            Log.d("PlexApi", "Resources v2 parsed: ${devices.size} devices")
            devices.forEach { d -> Log.d("PlexApi", "  Device: name=${d.name}, provides=${d.provides}, product=${d.product}, connections=${d.connections?.size}, accessToken=${d.accessToken?.take(4)}") }
            devices
        } catch (e: Exception) {
            Log.w("PlexApi", "Resources v2 call failed: ${e.message}", e)
            null
        }

        // Fallback: /api/resources.json
        val resources = resourcesFromPrimary ?: try {
            val response = apiCall("List Plex resources (fallback)") { client.get("https://plex.tv/api/resources.json") {
                parameter("includeHttps", 1)
                plexHeaders(accountToken)
            } }
            val bodyString = response.body<String>()
            Log.d("PlexApi", "Resources .json Content-Type: ${response.headers[HttpHeaders.ContentType] ?: "unknown"}")
            Log.d("PlexApi", "Resources .json body: ${bodyString.take(1000)}")
            val devices = plexJson.decodeFromString<List<PlexResource>>(bodyString)
            Log.d("PlexApi", "Resources .json parsed: ${devices.size} devices")
            devices
        } catch (e: Exception) {
            Log.w("PlexApi", "Resources .json fallback failed: ${e.message}", e)
            null
        } ?: return null

        Log.d("PlexApi", "serverInfo() found ${resources.size} resources, filtering for server")
        val serverResource = resources
            .firstOrNull { it.provides?.contains("server", ignoreCase = true) == true || it.product != null }
        Log.d("PlexApi", "serverInfo() selected resource: ${serverResource?.name}")
        return serverResource?.let { resource ->
            val validConnections = resource.connections.orEmpty()
            Log.d("PlexApi", "serverInfo() resource ${resource.name} has ${validConnections.size} connections")
            validConnections.forEach { c -> Log.d("PlexApi", "  Connection: uri=${c.uri}, address=${c.address}, port=${c.port}, local=${c.local}, relay=${c.relay}") }
            val bestUri = (validConnections.firstOrNull { it.local && !it.relay }
                ?: validConnections.firstOrNull { !it.relay }
                ?: validConnections.firstOrNull())?.let { conn ->
                    conn.uri ?: if (conn.address != null && conn.port != null) {
                        "${conn.protocol ?: "http"}://${conn.address}:${conn.port}"
                    } else null
                }
            Log.d("PlexApi", "serverInfo() bestUri: $bestUri")
            PlexServerInfo(name = resource.name, uri = bestUri, accessToken = resource.accessToken)
        }?.also { Log.d("PlexApi", "serverInfo() returning: name=${it.name}, uri=${it.uri}, accessToken=${it.accessToken?.take(4)}") }
    }

    suspend fun serverName(accountToken: String): String? = serverInfo(accountToken)?.name

    suspend fun tvShowSections(serverUrl: String, accountToken: String): List<PlexSectionDirectory> {
        val response = apiCall("List library sections") { client.get("${serverUrl.trimEnd('/')}/library/sections") {
            plexHeaders(accountToken)
        } }.body<PlexSectionsResponse>()
        return response.mediaContainer?.directory.orEmpty().filter { it.type == "show" }
    }

    /**
     * Continue Watching. Progress comes from [PlexOnDeckUserState] where the server provides it.
     * Posters are requested at the size the UI draws them, which is what keeps Plex resizing them.
     */
    suspend fun onDeck(
        serverUrl: String,
        accountToken: String,
        posterWidthPx: Int,
        posterHeightPx: Int,
        limit: Int
    ): List<OnDeckItem> {
        val response = apiCall("On Deck") { client.get("${serverUrl.trimEnd('/')}/library/onDeck") {
            // Plex's own clients cap the row in the request rather than trimming it after the fact,
            // so the Continue Watching shelf holds as many cards as it is meant to show.
            parameter("X-Plex-Container-Start", 0)
            parameter("X-Plex-Container-Size", limit)
            plexHeaders(accountToken)
        } }.body<PlexOnDeckResponse>()

        Log.d(
            API_LOG_TAG,
            "On Deck: ${response.mediaContainer?.size} of ${response.mediaContainer?.totalSize} items requested"
        )

        return response.mediaContainer?.metadata.orEmpty().mapNotNull { item ->
            val ratingKey = item.ratingKey ?: return@mapNotNull null
            // Episodes carry a show poster on grandparentThumb and a season poster on parentThumb;
            // their own thumb is only a still frame. The show poster is what Plex's own clients
            // lead with, so it wins here too.
            val posterPath = item.grandparentThumb ?: item.parentThumb ?: item.thumb
            OnDeckItem(
                ratingKey = ratingKey,
                title = item.title,
                grandparentTitle = item.grandparentTitle,
                thumb = posterPath
                    ?.let { PlexImageUrl.build(serverUrl, accountToken, it, posterWidthPx, posterHeightPx) }
                    .orEmpty(),
                viewOffset = item.userState?.viewOffset ?: item.viewOffset ?: 0L,
                duration = item.userState?.duration ?: item.duration ?: 0L,
                type = item.type
            ).also { onDeckItem ->
                Log.d(
                    API_LOG_TAG,
                    "On Deck item: $ratingKey, type=${item.type}, title=${onDeckItem.displayTitle}, " +
                        "offset=${onDeckItem.viewOffset}/${onDeckItem.duration}"
                )
            }
        }
    }

    suspend fun recentEpisodes(
        serverUrl: String,
        accountToken: String,
        sectionKey: String,
        limit: Int = 30
    ): List<PlexEpisode> {
        val response = apiCall("List recent episodes") { client.get("${serverUrl.trimEnd('/')}/library/sections/$sectionKey/recentlyAdded") {
            parameter("type", 4)
            parameter("X-Plex-Container-Start", 0)
            parameter("X-Plex-Container-Size", limit)
            plexHeaders(accountToken)
        } }.body<PlexMediaContainerResponse>()

        return response.mediaContainer?.metadata.orEmpty().mapNotNull { item ->
            val partKey = item.media.orEmpty().firstNotNullOfOrNull { mediaItem ->
                mediaItem.part.orEmpty().firstNotNullOfOrNull { it.key }
            } ?: return@mapNotNull null

            PlexEpisode(
                ratingKey = item.ratingKey,
                showTitle = item.grandparentTitle,
                episodeTitle = item.title,
                seasonNumber = item.parentIndex,
                episodeNumber = item.index,
                partKey = partKey
            )
        }.take(limit)
    }

    /**
     * The most recently added items in a library, newest first, which is what the server's
     * `recentlyAdded` endpoint means.
     *
     * [type] is Plex's item type filter, one of the `PLEX_ITEM_TYPE_*` numbers.
     *
     * Which image a card shows depends on what the row is: an episode carries a still frame of
     * itself, and a row of those reads as a row of noise, so episodes borrow the show's cover art.
     * A movie is its own artwork already.
     */
    suspend fun recentlyAddedInSection(
        serverUrl: String,
        accountToken: String,
        sectionKey: String,
        type: Int?,
        limit: Int,
        posterWidthPx: Int,
        posterHeightPx: Int
    ): List<PlexLibraryItem> {
        val response = apiCall("Recently added") {
            client.get("${serverUrl.trimEnd('/')}/library/sections/${sectionKey.trimStart('/')}/recentlyAdded") {
                type?.let { parameter("type", it) }
                parameter("X-Plex-Container-Start", 0)
                parameter("X-Plex-Container-Size", limit)
                plexHeaders(accountToken)
            }
        }.body<PlexMediaContainerResponse>()

        val items = response.mediaContainer?.metadata.orEmpty().mapNotNull { item ->
            val ratingKey = item.ratingKey ?: return@mapNotNull null
            val poster = if (type == PLEX_ITEM_TYPE_EPISODE) {
                item.grandparentThumb ?: item.thumb
            } else {
                item.thumb ?: item.grandparentThumb
            } ?: return@mapNotNull null
            PlexLibraryItem(
                ratingKey = ratingKey,
                title = listOfNotNull(item.grandparentTitle, item.title)
                    .joinToString(" - ")
                    .ifEmpty { null },
                thumb = PlexImageUrl.build(serverUrl, accountToken, poster, posterWidthPx, posterHeightPx),
                addedAt = item.addedAt,
                // Episodes open their show's detail page; movies have no parent show to navigate to.
                // Plex sends grandparentKey as a full `/library/metadata/{key}` path here, so it is
                // reduced to a bare rating key the show screen can build its own URL from.
                showRatingKey = if (type == PLEX_ITEM_TYPE_EPISODE) plexRatingKey(item.grandparentKey) else null,
                showTitle = if (type == PLEX_ITEM_TYPE_EPISODE) item.grandparentTitle else null
            )
        }
        Log.d(API_LOG_TAG, "Recently added in $sectionKey (type=$type): ${items.size} of ${response.mediaContainer?.totalSize ?: items.size}")
        return items
    }

    /**
     * The summary for a show: title, description, year and artwork, plus the season and episode
     * counts Plex reports on the metadata itself. Posters are requested at the size the header
     * draws them, the same as every other screen; [artWidthPx]/[artHeightPx] size the wide backdrop
     * separately, since it is fetched at full screen width rather than at poster size.
     */
    suspend fun showDetails(
        serverUrl: String,
        accountToken: String,
        showRatingKey: String,
        posterWidthPx: Int,
        posterHeightPx: Int,
        artWidthPx: Int,
        artHeightPx: Int
    ): PlexShow {
        val key = plexRatingKey(showRatingKey)
        if (key == null) {
            Log.w(API_LOG_TAG, "Show details called with a blank rating key")
            return PlexShow(
                ratingKey = "",
                title = null,
                summary = null,
                year = null,
                posterUrl = null,
                seasonCount = null,
                episodeCount = null
            )
        }
        val url = "${serverUrl.trimEnd('/')}/library/metadata/$key"
        Log.d(API_LOG_TAG, "Show details request: $url")
        val response = apiCall("Show details") {
            client.get(url) {
                plexHeaders(accountToken)
            }
        }
        Log.d(API_LOG_TAG, "Show details: status=${response.status.value}, contentType=${response.headers[HttpHeaders.ContentType]}")
        val bodyString = response.bodyAsText()
        Log.d(API_LOG_TAG, "Show details body: ${bodyString.take(800)}")

        val parsed = plexJson.decodeFromString<PlexShowDetailsResponse>(bodyString)
        val metadata = parsed.mediaContainer?.metadata.orEmpty().firstOrNull()
        Log.d(
            API_LOG_TAG,
            "Show details parsed: title=${metadata?.title}, seasons=${metadata?.childCount}, episodes=${metadata?.leafCount}"
        )

        val posterUrl = metadata?.thumb
            ?.let { PlexImageUrl.build(serverUrl, accountToken, it, posterWidthPx, posterHeightPx) }

        // A show with no art of its own still has a poster, and a backdrop is better than none.
        val artUrl = (metadata?.art ?: metadata?.thumb)
            ?.let { PlexImageUrl.build(serverUrl, accountToken, it, artWidthPx, artHeightPx) }

        return PlexShow(
            ratingKey = plexRatingKey(metadata?.ratingKey) ?: key,
            title = metadata?.title,
            summary = metadata?.summary,
            year = metadata?.year,
            posterUrl = posterUrl,
            seasonCount = metadata?.childCount,
            episodeCount = metadata?.leafCount,
            artUrl = artUrl,
            contentRating = metadata?.contentRating,
            rating = metadata?.audienceRating ?: metadata?.rating,
            tagline = metadata?.tagline,
            watchedEpisodeCount = metadata?.viewedLeafCount
        )
    }

    /**
     * Every episode in a show, for the carousel to draw and the row to split into seasons.
     * `/allLeaves?type=4` returns the show's whole episode set in one response, so the screen makes
     * a single request rather than one per season. `allLeaves` rather than `/all`: the latter
     * answers 404 on current Plex servers, while `allLeaves` is the same leaf set it used to return.
     *
     * The stills, headshots and poster are fetched at the sizes the screen draws them, since a card
     * asking Plex for an oversized image downloads the whole original.
     */
    suspend fun showEpisodes(
        serverUrl: String,
        accountToken: String,
        showRatingKey: String,
        stillWidthPx: Int,
        stillHeightPx: Int,
        avatarPx: Int
    ): List<PlexShowEpisode> {
        val key = plexRatingKey(showRatingKey)
        if (key == null) {
            Log.w(API_LOG_TAG, "Show episodes called with a blank rating key")
            return emptyList()
        }
        val url = "${serverUrl.trimEnd('/')}/library/metadata/$key/allLeaves"
        Log.d(API_LOG_TAG, "Show episodes request: $url?type=${PLEX_ITEM_TYPE_EPISODE}")
        val response = apiCall("Show episodes") {
            client.get(url) {
                parameter("type", PLEX_ITEM_TYPE_EPISODE)
                plexHeaders(accountToken)
            }
        }
        Log.d(API_LOG_TAG, "Show episodes: status=${response.status.value}, contentType=${response.headers[HttpHeaders.ContentType]}")
        val bodyString = response.bodyAsText()
        Log.d(API_LOG_TAG, "Show episodes body: ${bodyString.take(800)}")

        val parsed = plexJson.decodeFromString<PlexShowEpisodesResponse>(bodyString)
        val episodes = parsed.mediaContainer?.metadata.orEmpty().mapNotNull { item ->
            // Reduced the same way as the show key: the player builds `/library/metadata/{key}`
            // from whatever this row navigates with, so it must never be a path.
            val ratingKey = plexRatingKey(item.ratingKey) ?: return@mapNotNull null
            fun sized(path: String?, widthPx: Int, heightPx: Int): String? =
                path?.takeIf { it.isNotBlank() }
                    ?.let { PlexImageUrl.build(serverUrl, accountToken, it, widthPx, heightPx) }

            PlexShowEpisode(
                ratingKey = ratingKey,
                title = item.title,
                seasonNumber = item.parentIndex,
                episodeNumber = item.index,
                duration = item.duration,
                viewOffset = item.viewOffset,
                seasonTitle = item.parentTitle,
                thumbUrl = sized(item.thumb, stillWidthPx, stillHeightPx),
                summary = item.summary,
                airDate = item.originallyAvailableAt,
                contentRating = item.contentRating,
                rating = item.audienceRating ?: item.rating,
                ratingSource = item.audienceRatingImage ?: item.ratingImage,
                isWatched = (item.viewCount ?: 0) > 0,
                // Plex bills cast in `role` and crew in `director`; both carry a headshot for the
                // Cast and Crew row, at its own smaller size.
                directors = item.director.orEmpty().mapNotNull { person ->
                    person.toShowPerson(serverUrl, accountToken, avatarPx)
                },
                actors = item.role.orEmpty().mapNotNull { person ->
                    person.toShowPerson(serverUrl, accountToken, avatarPx)
                }
            )
        }
        Log.d(API_LOG_TAG, "Show episodes for $showRatingKey: ${episodes.size}")
        return episodes
    }

    /** A credited person, or null when Plex left them unnamed. */
    private fun PlexPerson.toShowPerson(
        serverUrl: String,
        accountToken: String,
        avatarPx: Int
    ): PlexShowPerson? {
        val name = tag?.takeIf { it.isNotBlank() } ?: return null
        val imageUrl = thumb?.takeIf { it.isNotBlank() }
            ?.let { PlexImageUrl.build(serverUrl, accountToken, it, avatarPx, avatarPx) }
        return PlexShowPerson(name = name, imageUrl = imageUrl)
    }

    /**
     * Marks an episode watched or unwatched, which is what the check button under the focused
     * episode does. Plex files this as a scrobble against the library plugin, with the item's own
     * rating key in both the query and the form body.
     */
    suspend fun setEpisodeWatched(
        serverUrl: String,
        accountToken: String,
        ratingKey: String,
        watched: Boolean
    ) {
        val key = plexRatingKey(ratingKey)
        if (key == null) {
            Log.w(API_LOG_TAG, "setEpisodeWatched called with a blank rating key")
            return
        }
        val url = "${serverUrl.trimEnd('/')}/:/scrobble"
        Log.d(API_LOG_TAG, "Scrobble $key as ${if (watched) "watched" else "unwatched"}")
        apiCall("Mark ${if (watched) "watched" else "unwatched"}") {
            client.put(url) {
                parameter("key", key)
                parameter("identifier", PLEX_LIBRARY_PLUGIN_ID)
                if (!watched) parameter("unwatched", 1)
                plexHeaders(accountToken)
                setBody(FormDataContent(Parameters.build {
                    append("ratingKey", key)
                    append("identifier", PLEX_LIBRARY_PLUGIN_ID)
                }))
            }
        }
    }

    /**
     * The part to play for any library item, episode or movie, by rating key.
     *
     * On Deck and the library shelves know which item a card stands for, but playback needs the
     * media and part behind it, which only the item's own metadata carries. Returns null when the
     * item has nothing playable behind it.
     */
    suspend fun mediaForRatingKey(
        serverUrl: String,
        accountToken: String,
        ratingKey: String
    ): PlexEpisode? {
        val response = apiCall("Item metadata") {
            client.get("${serverUrl.trimEnd('/')}/library/metadata/${ratingKey.trimStart('/')}") {
                plexHeaders(accountToken)
            }
        }.body<PlexMediaContainerResponse>()

        return response.mediaContainer?.metadata.orEmpty().firstNotNullOfOrNull { item ->
            val partKey = item.media.orEmpty().firstNotNullOfOrNull { mediaItem ->
                mediaItem.part.orEmpty().firstNotNullOfOrNull { it.key }
            } ?: return@firstNotNullOfOrNull null

            PlexEpisode(
                ratingKey = item.ratingKey,
                showTitle = item.grandparentTitle,
                episodeTitle = item.title,
                seasonNumber = item.parentIndex,
                episodeNumber = item.index,
                partKey = partKey
            )
        }
    }

    /** Asks Plex whether this device can play the selected episode directly or needs an HLS stream. */
    suspend fun playbackPlan(
        serverUrl: String,
        accountToken: String,
        episode: PlexEpisode,
        profile: PlexPlaybackProfile,
        sessionIdentifier: String
    ): PlexPlaybackPlan {
        val ratingKey = requireNotNull(episode.ratingKey) { "The selected episode has no Plex rating key." }
        var decision = requestPlaybackDecision(
            serverUrl = serverUrl,
            accountToken = accountToken,
            ratingKey = ratingKey,
            profile = profile,
            sessionIdentifier = sessionIdentifier,
            directPlay = true,
            directStreamAudio = true
        )
        var forceAudioTranscode = decision.hasUnsupportedDirectPlayAudio(profile)
        if (forceAudioTranscode && decision.isMdeDirectPlay()) {
            Log.w(
                PLAYBACK_LOG_TAG,
                "MDE returned direct play for audio the current profile excludes; forcing a transcode decision"
            )
            decision = requestPlaybackDecision(
                serverUrl = serverUrl,
                accountToken = accountToken,
                ratingKey = ratingKey,
                profile = profile,
                sessionIdentifier = sessionIdentifier,
                directPlay = false,
                directStreamAudio = false
            )
        }

        val response = decision.response
        val generalCode = decision.generalCode
        val directPlayCode = decision.directPlayCode
        val decisionText = decision.decisionText
        if (generalCode !in SUCCESSFUL_DECISION_CODES) {
            error(decisionText ?: "Plex cannot play this item on this device.")
        }

        val audioStreams = decision.audioStreams()
        val mdeDirectPlay = decision.isMdeDirectPlay()
        val isDirectPlay = !forceAudioTranscode && mdeDirectPlay
        Log.d(
            PLAYBACK_LOG_TAG,
            "MDE audio streams: ${audioStreams.map { it.codec }}, " +
                "forceAudioTranscode=$forceAudioTranscode"
        )

        return if (isDirectPlay) {
            val fallbackStatus = PlexPlaybackStatus(
                video = PlexStreamPlayback(PlexStreamMode.Direct),
                audio = PlexStreamPlayback(PlexStreamMode.Direct)
            )
            PlexPlaybackPlan.DirectPlay(
                url = "${serverUrl.trimEnd('/')}${episode.partKey}",
                decisionText = decisionText,
                playbackStatus = response.playbackStatus(fallbackStatus)
            ).also { plan -> Log.d(PLAYBACK_LOG_TAG, "MDE plan: DirectPlay, status=${plan.playbackStatus}") }
        } else {
            val fallbackStatus = PlexPlaybackStatus(
                video = PlexStreamPlayback(PlexStreamMode.Transcoded, "h264"),
                audio = PlexStreamPlayback(PlexStreamMode.Transcoded, "aac")
            )
            PlexPlaybackPlan.HlsTranscode(
                url = universalTranscodeUrl(
                    serverUrl = serverUrl,
                    accountToken = accountToken,
                    ratingKey = ratingKey,
                    profile = profile,
                    sessionIdentifier = sessionIdentifier,
                    directStreamAudio = !forceAudioTranscode
                ),
                decisionText = decisionText,
                playbackStatus = response.playbackStatus(fallbackStatus)
            ).also { plan -> Log.d(PLAYBACK_LOG_TAG, "MDE plan: HlsTranscode, status=${plan.playbackStatus}") }
        }
    }

    /** Updates Plex's playback timeline so resume progress stays in sync across clients. */
    suspend fun reportTimeline(
        serverUrl: String,
        accountToken: String,
        ratingKey: String,
        state: String,
        timeMillis: Long,
        durationMillis: Long,
        sessionIdentifier: String
    ) {
        apiCall("Report playback timeline ($state)") { client.get("${serverUrl.trimEnd('/')}/:/timeline") {
            parameter("ratingKey", ratingKey)
            parameter("key", "/library/metadata/$ratingKey")
            parameter("state", state)
            parameter("time", timeMillis)
            parameter("duration", durationMillis)
            plexHeaders(accountToken = accountToken, sessionIdentifier = sessionIdentifier)
        } }.also { Log.d(PLAYBACK_LOG_TAG, "Timeline response: state=$state, httpStatus=${it.status.value}") }
    }

    /** Stops the Plex Universal Transcoder session created for this playback session. */
    suspend fun stopUniversalTranscodeSession(
        serverUrl: String,
        accountToken: String,
        sessionIdentifier: String
    ) {
        apiCall("Stop universal transcode session") { client.get("${serverUrl.trimEnd('/')}/video/:/transcode/universal/stop") {
            parameter("session", sessionIdentifier)
            plexHeaders(accountToken = accountToken, sessionIdentifier = sessionIdentifier)
        } }.also { Log.d(PLAYBACK_LOG_TAG, "Stop transcode session response: httpStatus=${it.status.value}") }
    }

    /**
     * Opens Plex's notification firehose and hands every raw frame to [onFrame]. Returns the close
     * reason when the server ends the stream, and null when it ended without one.
     */
    suspend fun observeNotificationFrames(
        serverUrl: String,
        accountToken: String,
        onFrame: suspend (Frame) -> Unit
    ): CloseReason? {
        val url = URLBuilder("${serverUrl.trimEnd('/')}$NOTIFICATIONS_PATH").apply {
            protocol = if (serverUrl.startsWith("https://", ignoreCase = true)) {
                URLProtocol.WSS
            } else {
                URLProtocol.WS
            }
            parameters.append("X-Plex-Token", accountToken)
        }.buildString()
        Log.d(API_LOG_TAG, "WebSocket connecting to ${url.replace(accountToken, "<token>")}")
        val session = client.webSocketSession(urlString = url)
        Log.d(API_LOG_TAG, "WebSocket connected")
        return try {
            for (frame in session.incoming) onFrame(frame)
            session.closeReason.await()
        } finally {
            session.close()
        }
    }

    fun close() = client.close()

    /**
     * Every library on the server. Which of them a screen shows is a naming decision rather than an
     * API one, so it lives in [PlexLibrarySections].
     */
    suspend fun librarySections(serverUrl: String, accountToken: String): List<PlexSectionDirectory> {
        val response = apiCall("Libraries") {
            client.get("${serverUrl.trimEnd('/')}/library/sections") {
                plexHeaders(accountToken)
            }
        }.body<PlexSectionsResponse>()

        val sections = response.mediaContainer?.directory.orEmpty()
        Log.d(API_LOG_TAG, "Libraries: ${sections.size}")
        sections.forEach { section ->
            Log.d(
                API_LOG_TAG,
                "  section key=${section.key}, title=${section.title}, type=${section.type}"
            )
        }
        return sections
    }

    /**
     * Everything in one library, newest additions first as Plex orders them. Posters are requested at
     * the size the shelf draws them, the same as On Deck.
     */
    suspend fun libraryItems(
        serverUrl: String,
        accountToken: String,
        sectionKey: String,
        posterWidthPx: Int,
        posterHeightPx: Int
    ): List<PlexLibraryItem> {
        val response = apiCall("Library items") {
            client.get("${serverUrl.trimEnd('/')}/library/sections/${sectionKey.trimStart('/')}/all") {
                plexHeaders(accountToken)
            }
        }.body<PlexSectionItemsResponse>()

        val items = PlexLibrarySections.orderByRecentlyAdded(
            response.mediaContainer?.metadata.orEmpty().mapNotNull { item ->
                val ratingKey = item.ratingKey ?: return@mapNotNull null
                val thumb = item.thumb ?: return@mapNotNull null
                PlexLibraryItem(
                    ratingKey = ratingKey,
                    title = item.title,
                    thumb = PlexImageUrl.build(
                        serverUrl,
                        accountToken,
                        thumb,
                        posterWidthPx,
                        posterHeightPx
                    ),
                    addedAt = item.addedAt
                )
            }
        )
        val newest = items.firstOrNull()
        Log.d(
            API_LOG_TAG,
            "Library $sectionKey: ${items.size} items of ${response.mediaContainer?.totalSize ?: items.size}, " +
                "newest first: ${newest?.title} addedAt=${newest?.addedAt}"
        )
        return items
    }

    private suspend fun <T> apiCall(operation: String, request: suspend () -> T): T {
        Log.d(API_LOG_TAG, "Request: $operation")
        return try {
            request().also { Log.d(API_LOG_TAG, "Completed: $operation") }
        } catch (e: CancellationException) {
            Log.d(API_LOG_TAG, "Cancelled: $operation")
            throw e
        } catch (e: Exception) {
            Log.w(
                API_LOG_TAG,
                "Failed: $operation; type=${e.javaClass.simpleName}, message=${e.message}"
            )
            throw e
        }
    }

    private suspend fun requestPlaybackDecision(
        serverUrl: String,
        accountToken: String,
        ratingKey: String,
        profile: PlexPlaybackProfile,
        sessionIdentifier: String,
        directPlay: Boolean,
        directStreamAudio: Boolean
    ): PlaybackDecisionResult {
        val requestParameters = universalPlaybackParameters(
            ratingKey = ratingKey,
            profile = profile,
            sessionIdentifier = sessionIdentifier,
            directPlay = directPlay,
            directStreamAudio = directStreamAudio
        )
        val decisionHttpResponse = apiCall("Request playback decision") {
            client.get("${serverUrl.trimEnd('/')}/video/:/transcode/universal/decision") {
                requestParameters.forEach { (name, value) -> parameter(name, value) }
                parameter("X-Plex-Token", accountToken)
                plexHeaders(accountToken, profile, sessionIdentifier)
            }
        }
        Log.d(PLAYBACK_LOG_TAG, "MDE request URL: ${decisionHttpResponse.call.request.url}")
        Log.d(
            PLAYBACK_LOG_TAG,
            "MDE response: ratingKey=$ratingKey, httpStatus=${decisionHttpResponse.status.value}"
        )
        if (decisionHttpResponse.status.value !in 200..299) {
            val errorBody = decisionHttpResponse.body<String>()
            Log.e(PLAYBACK_LOG_TAG, "MDE error body: $errorBody")
            error("Plex MDE returned ${decisionHttpResponse.status}: $errorBody")
        }
        val response = decisionHttpResponse.body<PlexPlaybackDecisionResponse>().mediaContainer
            ?: error("Plex did not return a playback decision.")
        val generalCode = response.generalDecisionCode ?: response.mdeDecisionCode
        val directPlayCode = response.directPlayDecisionCode
            ?: if (response.mdeDecisionCode == DIRECT_PLAY_OK) DIRECT_PLAY_OK else null
        val decisionText = response.generalDecisionText
            ?: response.mdeDecisionText
            ?: response.directPlayDecisionText
        Log.d(
            PLAYBACK_LOG_TAG,
            "MDE decision: general=$generalCode (${response.generalDecisionText ?: response.mdeDecisionText}), " +
                "directPlay=$directPlayCode (${response.directPlayDecisionText})"
        )
        val result = PlaybackDecisionResult(response, generalCode, directPlayCode, decisionText)
        Log.d(
            PLAYBACK_LOG_TAG,
            "MDE response audio streams: ${result.audioStreams().map { it.codec }}"
        )
        return result
    }

    private fun PlaybackDecisionResult.audioStreams(): List<PlexPlaybackDecisionStream> =
        response.metadata.orEmpty().firstOrNull()
            ?.media.orEmpty().firstOrNull()
            ?.part.orEmpty().firstOrNull()
            ?.stream.orEmpty()
            .filter { it.streamType == AUDIO_STREAM_TYPE }

    private fun PlaybackDecisionResult.hasUnsupportedDirectPlayAudio(
        profile: PlexPlaybackProfile
    ): Boolean = audioStreams().any { stream ->
        val codec = stream.codec.normalizedAudioCodec()
        if (profile.directPlayAudioCodecs.isNotEmpty() && codec != null) {
            codec !in profile.directPlayAudioCodecs
        } else {
            codec.equals("flac", ignoreCase = true) ||
                (!profile.supportsEac3Directly && codec.isEac3Codec())
        }
    }

    private fun PlaybackDecisionResult.isMdeDirectPlay(): Boolean {
        val partDecision = response.metadata.orEmpty().firstOrNull()
            ?.media.orEmpty().firstOrNull()
            ?.part.orEmpty().firstOrNull()
            ?.decision
        return directPlayCode == DIRECT_PLAY_OK ||
            (directPlayCode == null && partDecision.equals("directplay", ignoreCase = true))
    }

    private fun universalPlaybackParameters(
        ratingKey: String,
        profile: PlexPlaybackProfile,
        sessionIdentifier: String,
        directPlay: Boolean,
        directStreamAudio: Boolean
    ) = mapOf(
        "path" to "/library/metadata/$ratingKey",
        "mediaIndex" to "0",
        "partIndex" to "0",
        "protocol" to "hls",
        "directPlay" to if (directPlay) "1" else "0",
        "directStream" to "1",
        "directStreamAudio" to if (directStreamAudio) "1" else "0",
        "hasMDE" to "1",
        "subtitles" to "none",
        "videoQuality" to "99",
        "videoResolution" to "3840x2160",
        "X-Plex-Client-Identifier" to clientIdentifier,
        "X-Plex-Client-Profile-Name" to PlexPlaybackProfile.GENERIC_PROFILE_NAME,
        "X-Plex-Client-Profile-Extra" to profile.clientProfileExtra,
        "X-Plex-Session-Identifier" to sessionIdentifier,
        "session" to sessionIdentifier
    )

    private fun universalTranscodeUrl(
        serverUrl: String,
        accountToken: String,
        ratingKey: String,
        profile: PlexPlaybackProfile,
        sessionIdentifier: String,
        directStreamAudio: Boolean
    ): String = URLBuilder("${serverUrl.trimEnd('/')}/video/:/transcode/universal/start.m3u8").apply {
        universalPlaybackParameters(
            ratingKey = ratingKey,
            profile = profile,
            sessionIdentifier = sessionIdentifier,
            directPlay = false,
            directStreamAudio = directStreamAudio
        )
            .forEach { (name, value) -> parameters.append(name, value) }
        parameters.append("X-Plex-Token", accountToken)
    }.buildString()

    private fun PlexPlaybackDecisionContainer.playbackStatus(
        fallback: PlexPlaybackStatus
    ): PlexPlaybackStatus {
        val streams = metadata.orEmpty().firstOrNull()
            ?.media.orEmpty().firstOrNull()
            ?.part.orEmpty().firstOrNull()
            ?.stream.orEmpty()
        return PlexPlaybackStatus(
            video = streams.firstOrNull { it.streamType == VIDEO_STREAM_TYPE }
                ?.toPlaybackStatus() ?: fallback.video,
            audio = streams.firstOrNull { it.streamType == AUDIO_STREAM_TYPE }
                ?.toPlaybackStatus() ?: fallback.audio
        )
    }

    private fun String?.normalizedAudioCodec(): String? = when {
        isEac3Codec() -> "eac3"
        equals("flac", ignoreCase = true) -> "flac"
        else -> this?.lowercase()
    }

    private fun String?.isEac3Codec(): Boolean =
        equals("eac3", ignoreCase = true) ||
            equals("e-ac-3", ignoreCase = true) ||
            equals("ec-3", ignoreCase = true)

    private fun PlexPlaybackDecisionStream.toPlaybackStatus() = PlexStreamPlayback(
        mode = if (decision.equals("transcode", ignoreCase = true)) {
            PlexStreamMode.Transcoded
        } else {
            PlexStreamMode.Direct
        },
        codec = codec
    )

    private fun io.ktor.client.request.HttpRequestBuilder.plexHeaders(
        accountToken: String? = null,
        profile: PlexPlaybackProfile? = null,
        sessionIdentifier: String? = null
    ) {
        accept(ContentType.Application.Json)
        header("X-Plex-Client-Identifier", clientIdentifier)
        header("X-Plex-Product", "Player Exp")
        header("X-Plex-Version", "1.0")
        header("X-Plex-Platform", "Android TV")
        header(HttpHeaders.Accept, ContentType.Application.Json)
        profile?.let {
            header("X-Plex-Client-Profile-Name", PlexPlaybackProfile.GENERIC_PROFILE_NAME)
            header("X-Plex-Client-Profile-Extra", it.clientProfileExtra)
        }
        sessionIdentifier?.let { header("X-Plex-Session-Identifier", it) }
        accountToken?.let { header("X-Plex-Token", it) }
    }

    private companion object {
        const val PLAYBACK_LOG_TAG = "PlexPlayback"
        const val API_LOG_TAG = "PlexApi"
        const val NOTIFICATIONS_PATH = "/:/websockets/notifications"
        val SUCCESSFUL_DECISION_CODES = 1000..1999
        const val DIRECT_PLAY_OK = 1000
        const val VIDEO_STREAM_TYPE = 1
        const val AUDIO_STREAM_TYPE = 2
    }
}
