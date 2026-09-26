package com.neilturner.playerexp.data.plex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexNotificationParserTest {

    @Test
    fun `reads a play position out of a playing notification`() {
        val frame = """
            {"NotificationContainer":{"type":"playing","size":1,"PlaySessionStateNotification":[
              {"sessionKey":"628","clientIdentifier":"980878c6a5f14965-com-plexapp-android",
               "guid":"","ratingKey":"46642","url":"","key":"/library/metadata/46642",
               "viewOffset":788796,"playQueueItemID":4821,"state":"playing"}]}}
        """.trimIndent()

        val events = PlexNotificationParser.parse(frame)

        assertEquals(1, events.size)
        val event = events.single()
        assertTrue(event is PlexLibraryEvent.ProgressChanged)
        event as PlexLibraryEvent.ProgressChanged
        assertEquals("46642", event.ratingKey)
        assertEquals(788_796L, event.viewOffset)
        assertEquals(null, event.duration)
    }

    @Test
    fun `reads a play position with a duration out of a timeline notification`() {
        val frame = """
            {"NotificationContainer":{"type":"timeline","size":1,"TimelineNotification":[
              {"ratingKey":"46163","itemID":"46163","parentID":"46162","rootID":"46161",
               "viewOffset":421000,"viewCount":1,"duration":1470980,"state":"playing"}]}}
        """.trimIndent()

        val event = PlexNotificationParser.parse(frame).single() as PlexLibraryEvent.ProgressChanged

        assertEquals("46163", event.ratingKey)
        assertEquals(421_000L, event.viewOffset)
        assertEquals(1_470_980L, event.duration)
    }

    @Test
    fun `treats library activity as a change to fetch`() {
        val frame = """
            {"NotificationContainer":{"type":"activity","size":1,"ActivityNotification":[
              {"event":"item.added","uuid":"3f2a","Activity":[
                {"uuid":"3f2a","event":"item.added","ratingKey":"47100","type":"episode"}]}]}}
        """.trimIndent()

        val event = PlexNotificationParser.parse(frame).single()

        assertEquals(PlexLibraryEvent.LibraryChanged("activity:item.added"), event)
    }

    @Test
    fun `treats a regenerated background queue and a reached event as changes`() {
        val background = """
            {"NotificationContainer":{"type":"backgroundProcessingQueue","size":1,
             "BackgroundProcessingQueueEventNotification":[{"queueID":1,"event":"queueRegenerated"}]}}
        """.trimIndent()
        val reached = """
            {"NotificationContainer":{"type":"reached","size":1,"ReachedNotification":[
              {"itemID":"46163","reached":1}]}}
        """.trimIndent()

        assertEquals(
            PlexLibraryEvent.LibraryChanged("backgroundQueue:queueRegenerated"),
            PlexNotificationParser.parse(background).single()
        )
        assertEquals(
            PlexLibraryEvent.LibraryChanged("reached"),
            PlexNotificationParser.parse(reached).single()
        )
    }

    @Test
    fun `reads every entry of a container that carries several`() {
        val frame = """
            {"NotificationContainer":{"type":"playing","size":2,"PlaySessionStateNotification":[
              {"ratingKey":"1","viewOffset":10,"state":"playing"},
              {"ratingKey":"2","viewOffset":20,"state":"paused"}]}}
        """.trimIndent()

        val events = PlexNotificationParser.parse(frame)

        assertEquals(2, events.size)
        assertEquals(listOf("1", "2"), events.map { (it as PlexLibraryEvent.ProgressChanged).ratingKey })
    }

    @Test
    fun `ignores notification types the app has no use for`() {
        val frame = """
            {"NotificationContainer":{"type":"device","size":1,"DeviceNotification":[
              {"name":"Living Room","product":"Plex for Android"}]}}
        """.trimIndent()

        assertTrue(PlexNotificationParser.parse(frame).isEmpty())
    }

    @Test
    fun `a frame that is not json comes back empty instead of throwing`() {
        assertTrue(PlexNotificationParser.parse("not json at all").isEmpty())
        assertTrue(PlexNotificationParser.parse("").isEmpty())
        assertTrue(PlexNotificationParser.parse("{\"NotificationContainer\":").isEmpty())
    }

    @Test
    fun `a play position without a rating key or offset is skipped`() {
        val frame = """
            {"NotificationContainer":{"type":"playing","size":2,"PlaySessionStateNotification":[
              {"sessionKey":"1","state":"playing"},
              {"ratingKey":"2","state":"playing"}]}}
        """.trimIndent()

        assertTrue(PlexNotificationParser.parse(frame).isEmpty())
    }
}
