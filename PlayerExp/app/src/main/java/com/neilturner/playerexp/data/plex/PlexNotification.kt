package com.neilturner.playerexp.data.plex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Plex's notification firehose, reduced to the two things the app reacts to.
 *
 * The socket carries every client's activity on the server, so most frames are about someone else
 * watching something. The parts worth acting on are a library or queue change, which can add or drop
 * rows, and a play position that moved, which can redraw the bar on a row that is already showing.
 */
sealed interface PlexLibraryEvent {
    /** The library, the on-deck queue or a scan changed, so the row has to be fetched again. */
    data class LibraryChanged(val source: String) : PlexLibraryEvent

    /** One item's play position moved. */
    data class ProgressChanged(
        val ratingKey: String,
        val viewOffset: Long,
        val duration: Long?
    ) : PlexLibraryEvent
}

@Serializable
private data class PlexNotificationEnvelope(
    @SerialName("NotificationContainer") val container: PlexNotificationContainer? = null
)

/**
 * One container per frame. Plex names the payload array after the type, and unknown types simply
 * have no field here, so a frame the app does not understand parses to nothing rather than failing.
 */
@Serializable
private data class PlexNotificationContainer(
    val type: String? = null,
    val size: Int? = null,
    @SerialName("PlaySessionStateNotification") val playing: List<PlexPlaySessionState> = emptyList(),
    @SerialName("TimelineNotification") val timeline: List<PlexTimelineEntry> = emptyList(),
    @SerialName("ActivityNotification") val activity: List<PlexActivityEntry> = emptyList(),
    @SerialName("ReachedNotification") val reached: List<PlexReachedEntry> = emptyList(),
    @SerialName("BackgroundProcessingQueueEventNotification")
    val backgroundQueue: List<PlexBackgroundQueueEntry> = emptyList()
)

@Serializable
private data class PlexPlaySessionState(
    val ratingKey: String? = null,
    val viewOffset: Long? = null,
    val state: String? = null
)

@Serializable
private data class PlexTimelineEntry(
    val ratingKey: String? = null,
    val viewOffset: Long? = null,
    val duration: Long? = null,
    val state: String? = null
)

@Serializable
private data class PlexActivityEntry(
    val event: String? = null,
    @SerialName("Activity") val activity: List<PlexActivityDetail> = emptyList()
)

@Serializable
private data class PlexActivityDetail(
    val ratingKey: String? = null,
    val event: String? = null,
    val type: String? = null
)

@Serializable
private data class PlexReachedEntry(
    val itemID: String? = null,
    val reached: Int? = null
)

@Serializable
private data class PlexBackgroundQueueEntry(
    val event: String? = null
)

/** Turns one websocket frame into the events worth acting on. */
object PlexNotificationParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * A malformed frame must never take the socket down, so anything unparseable comes back empty
     * and the connection carries on. Kept free of Android and logging calls so it stays testable on
     * the JVM.
     */
    fun parse(frameText: String): List<PlexLibraryEvent> {
        val container = try {
            json.decodeFromString<PlexNotificationEnvelope>(frameText).container
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        return buildList {
            container.playing.forEach { session ->
                val ratingKey = session.ratingKey ?: return@forEach
                val viewOffset = session.viewOffset ?: return@forEach
                add(
                    PlexLibraryEvent.ProgressChanged(
                        ratingKey = ratingKey,
                        viewOffset = viewOffset,
                        duration = null
                    )
                )
            }
            container.timeline.forEach { entry ->
                val ratingKey = entry.ratingKey ?: return@forEach
                val viewOffset = entry.viewOffset ?: return@forEach
                add(
                    PlexLibraryEvent.ProgressChanged(
                        ratingKey = ratingKey,
                        viewOffset = viewOffset,
                        duration = entry.duration
                    )
                )
            }
            if (container.activity.isNotEmpty()) {
                val summary = container.activity
                    .mapNotNull { it.event ?: it.activity.firstOrNull()?.event }
                    .distinct()
                    .joinToString(",")
                    .ifEmpty { "activity" }
                add(PlexLibraryEvent.LibraryChanged("activity:$summary"))
            }
            if (container.reached.isNotEmpty()) {
                add(PlexLibraryEvent.LibraryChanged("reached"))
            }
            if (container.backgroundQueue.isNotEmpty()) {
                val summary = container.backgroundQueue
                    .mapNotNull { it.event }
                    .distinct()
                    .joinToString(",")
                    .ifEmpty { "backgroundQueue" }
                add(PlexLibraryEvent.LibraryChanged("backgroundQueue:$summary"))
            }
        }
    }
}
