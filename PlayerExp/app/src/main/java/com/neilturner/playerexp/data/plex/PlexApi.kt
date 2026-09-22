package com.neilturner.playerexp.data.plex

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

@Serializable
data class PlexEpisodesContainer(
    @SerialName("Metadata") val metadata: List<PlexEpisodeMetadata>? = null
)

@Serializable
data class PlexEpisodeMetadata(
    val ratingKey: String? = null,
    val title: String? = null,
    val grandparentTitle: String? = null,
    val parentIndex: Int? = null,
    val index: Int? = null,
    @SerialName("Media") val media: List<PlexMediaItem>? = null
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

class PlexApi(private val clientIdentifier: String) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun createPin(): PlexPin = client.post("https://plex.tv/api/v2/pins") {
        plexHeaders()
    }.body()

    suspend fun pin(pinId: Long): PlexPin = client.get("https://plex.tv/api/v2/pins/$pinId") {
        plexHeaders()
    }.body()

    suspend fun serverInfo(accountToken: String): PlexServerInfo? {
        val plexJson = Json { ignoreUnknownKeys = true; isLenient = true }
        Log.d("PlexApi", "serverInfo() called, token present: ${accountToken.isNotBlank()}")

        // Primary: /api/v2/resources
        val resourcesFromPrimary = try {
            val response = client.get("https://plex.tv/api/v2/resources") {
                parameter("includeHttps", 1)
                plexHeaders(accountToken)
            }
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
            val response = client.get("https://plex.tv/api/resources.json") {
                parameter("includeHttps", 1)
                plexHeaders(accountToken)
            }
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
        val response = client.get("${serverUrl.trimEnd('/')}/library/sections") {
            plexHeaders(accountToken)
        }.body<PlexSectionsResponse>()
        return response.mediaContainer?.directory.orEmpty().filter { it.type == "show" }
    }

    suspend fun recentEpisodes(
        serverUrl: String,
        accountToken: String,
        sectionKey: String,
        limit: Int = 10
    ): List<PlexEpisode> {
        val response = client.get("${serverUrl.trimEnd('/')}/library/sections/$sectionKey/recentlyAdded") {
            parameter("type", 4)
            parameter("X-Plex-Container-Start", 0)
            parameter("X-Plex-Container-Size", limit)
            plexHeaders(accountToken)
        }.body<PlexMediaContainerResponse>()

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

    fun close() = client.close()

    private fun io.ktor.client.request.HttpRequestBuilder.plexHeaders(accountToken: String? = null) {
        accept(ContentType.Application.Json)
        header("X-Plex-Client-Identifier", clientIdentifier)
        header("X-Plex-Product", "Player Exp")
        header("X-Plex-Version", "1.0")
        header("X-Plex-Platform", "Android TV")
        header(HttpHeaders.Accept, ContentType.Application.Json)
        accountToken?.let { header("X-Plex-Token", it) }
    }
}
