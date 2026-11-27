package com.neilturner.visualmashup.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollingCollageLayoutTest {

    private fun image(width: Int, height: Int) = SourceImage(
        id = 1,
        url = "test://image",
        width = width,
        height = height,
        orientation = when {
            width > height -> ImageOrientation.LANDSCAPE
            width < height -> ImageOrientation.PORTRAIT
            else -> ImageOrientation.SQUARE
        },
    )

    @Test
    fun `tile aspect follows the image ratio inside the clamp range`() {
        assertEquals(1.5f, ScrollingCollageLayout.tileAspect(image(1920, 2880)), 1e-4f)
        assertEquals(1.25f, ScrollingCollageLayout.tileAspect(image(1920, 2400)), 1e-4f)
        assertEquals(1f, ScrollingCollageLayout.tileAspect(image(1920, 1920)), 1e-4f)
    }

    @Test
    fun `panoramas are clamped to the minimum tile aspect`() {
        assertEquals(0.65f, ScrollingCollageLayout.tileAspect(image(1920, 202)), 1e-4f)
        assertEquals(0.65f, ScrollingCollageLayout.tileAspect(image(1920, 1080)), 1e-4f)
    }

    @Test
    fun `very tall images are clamped to the maximum tile aspect`() {
        assertEquals(1.5f, ScrollingCollageLayout.tileAspect(image(1080, 2400)), 1e-4f)
    }
}
