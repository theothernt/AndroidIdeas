package com.neilturner.visualmashup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageLayoutsTest {

    private val epsilon = 1e-4f

    private fun image(id: Int, width: Int, height: Int) = SourceImage(
        id = id,
        url = "test://image/$id",
        width = width,
        height = height,
        orientation = when {
            width > height -> ImageOrientation.LANDSCAPE
            width < height -> ImageOrientation.PORTRAIT
            else -> ImageOrientation.SQUARE
        },
    )

    private val portraitA = image(1, 1920, 2880)
    private val portraitB = image(2, 1920, 2560)
    private val landscapeA = image(3, 1920, 1280)
    private val landscapeB = image(4, 1920, 1080)
    private val square = image(5, 1920, 1920)

    @Test
    fun `one image is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CollageLayouts.bestFor(listOf(portraitA))
        }
    }

    @Test
    fun `every image is used exactly once`() {
        val images = listOf(landscapeA, portraitA, square)
        val placement = CollageLayouts.bestFor(images)

        assertEquals(images.size, placement.template.imageCount)
        assertEquals(images.size, placement.images.size)
        assertEquals(images.map { it.id }.sorted(), placement.images.map { it.id }.sorted())
    }

    @Test
    fun `the chosen layout keeps the most visible area`() {
        val sets = listOf(
            listOf(portraitA, portraitB),
            listOf(landscapeA, landscapeB),
            listOf(landscapeA, portraitA, square),
            listOf(portraitA, portraitB, square),
        )

        for (images in sets) {
            val placement = CollageLayouts.bestFor(images)
            assertEquals(
                "wrong layout for ${images.map { it.id }}",
                bestVisibleArea(images),
                placement.visibleAreaFraction,
                epsilon,
            )
        }
    }

    @Test
    fun `counts without a dedicated template use the grid fallback`() {
        val images = (1..7).map { image(it, 1600, 1200) }
        val placement = CollageLayouts.bestFor(images)

        assertEquals("grid_7", placement.template.id)
        assertEquals(7, placement.images.size)
        assertTrue(placement.visibleAreaFraction > 0f)
    }

    @Test
    fun `the visible fraction is never more than the whole image`() {
        val placement = CollageLayouts.bestFor(listOf(portraitA, landscapeA))

        assertTrue(placement.visibleAreaFraction > 0f)
        assertTrue(placement.visibleAreaFraction <= 1f + epsilon)
    }

    @Test
    fun `repeated calls reuse the solved placement`() {
        val images = listOf(landscapeA, portraitA, square, square)

        assertSame(CollageLayouts.bestFor(images), CollageLayouts.bestFor(images))
    }

    /** Brute-force oracle: the documented metric over every template and image order. */
    private fun bestVisibleArea(images: List<SourceImage>): Float {
        val templates = CollageTemplates.forCount(images.size)
            .ifEmpty { listOf(CollageTemplates.gridFor(images.size)) }

        return templates.maxOf { template ->
            permutations(images).maxOf { order ->
                template.cells.mapIndexed { index, cell ->
                    val cellAspect = cell.width * CollageLayouts.CANVAS_ASPECT / cell.height
                    val imageAspect = order[index].width.toFloat() / order[index].height
                    val visible = minOf(cellAspect / imageAspect, imageAspect / cellAspect)
                    visible * cell.width * cell.height
                }.sum()
            }
        }
    }

    private fun <T> permutations(items: List<T>): List<List<T>> {
        if (items.size <= 1) return listOf(items)
        return items.flatMapIndexed { index, item ->
            val rest = items.toMutableList().apply { removeAt(index) }
            permutations(rest).map { listOf(item) + it }
        }
    }
}
