package com.neilturner.playerexp.data.plex

import org.junit.Assert.assertEquals
import org.junit.Test

class PlexViewOffsetTest {

    @Test
    fun `an unwatched item starts from the beginning`() {
        assertEquals(0L, resolveViewOffset(viewOffset = null, userState = null, duration = 3_600_000L))
    }

    @Test
    fun `resumes part-way through the item`() {
        assertEquals(1_200_000L, resolveViewOffset(1_200_000L, null, 3_600_000L))
    }

    @Test
    fun `older servers put the position in the user state block`() {
        val userState = PlexItemUserState(viewOffset = 900_000L)

        assertEquals(900_000L, resolveViewOffset(null, userState, 3_600_000L))
    }

    @Test
    fun `a finished item starts over rather than at the end`() {
        val finished = 3_600_000L

        assertEquals(0L, resolveViewOffset(finished, null, finished))
        assertEquals(0L, resolveViewOffset(finished + 1L, null, finished))
    }

    @Test
    fun `a position past the end is held when the server sent no duration`() {
        assertEquals(4_000_000L, resolveViewOffset(4_000_000L, null, null))
    }

    @Test
    fun `a zeroed or negative position starts fresh`() {
        assertEquals(0L, resolveViewOffset(0L, null, 3_600_000L))
        assertEquals(0L, resolveViewOffset(-500L, null, 3_600_000L))
    }

    @Test
    fun `a missing user state falls back to no position at all`() {
        assertEquals(0L, resolveViewOffset(null, PlexItemUserState(), 3_600_000L))
    }
}
