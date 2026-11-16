package com.neilturner.overlayparty.domain.overlay

import com.neilturner.overlayparty.ui.overlay.OverlayContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationMessageToOverlayContentUseCaseTest {
    private val useCase = LocationMessageToOverlayContentUseCase()

    @Test
    fun `message and location are limited to two lines`() {
        val content = useCase(location = "location", message = "message")

        assertTrue(content is OverlayContent.VerticalStack)
        val items = (content as OverlayContent.VerticalStack).items
        assertEquals(2, items.size)
        assertTrue(items.all { it is OverlayContent.TextOnly && it.maxLines == 2 })
    }

    @Test
    fun `blank content returns null`() {
        val content = useCase(location = " ", message = "")

        assertTrue(content == null)
    }
}
