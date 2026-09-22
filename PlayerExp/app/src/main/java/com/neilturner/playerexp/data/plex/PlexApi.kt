package com.neilturner.playerexp.data.plex

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
data class PlexResource(
    val name: String,
    val product: String? = null,
    val provides: String? = null
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

    suspend fun serverName(accountToken: String): String? = client.get("https://plex.tv/api/resources") {
        parameter("includeHttps", 1)
        plexHeaders(accountToken)
    }.body<List<PlexResource>>()
        .firstOrNull { it.provides?.contains("server", ignoreCase = true) == true || it.product != null }
        ?.name

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
