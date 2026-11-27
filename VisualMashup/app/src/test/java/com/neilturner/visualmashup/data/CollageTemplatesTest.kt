package com.neilturner.visualmashup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.sqrt

class CollageTemplatesTest {

    private val epsilon = 1e-4f

    @Test
    fun `dedicated templates tile the canvas`() {
        for (count in 2..6) {
            val templates = CollageTemplates.forCount(count)
            assertTrue("expected templates for $count images", templates.isNotEmpty())
            for (template in templates) {
                assertEquals("${template.id} should hold $count images", count, template.imageCount)
                assertCellsAreInBounds(template)
                assertCellsDoNotOverlap(template)

                val area = template.cells.sumOf { (it.width * it.height).toDouble() }.toFloat()
                assertEquals("${template.id} should cover the canvas", 1f, area, epsilon)
            }
        }
    }

    @Test
    fun `grid fallback keeps every image in its own cell`() {
        for (count in 2..12) {
            val template = CollageTemplates.gridFor(count)
            assertEquals(count, template.imageCount)
            assertCellsAreInBounds(template)
            assertCellsDoNotOverlap(template)

            val cols = ceil(sqrt(count.toDouble())).toInt()
            val rows = ceil(count.toDouble() / cols).toInt()
            val area = template.cells.sumOf { (it.width * it.height).toDouble() }.toFloat()
            assertEquals(
                "grid_$count should cover one cell per image",
                count.toFloat() / (cols * rows),
                area,
                epsilon,
            )
        }
    }

    @Test
    fun `a collage needs at least two images`() {
        assertThrows(IllegalArgumentException::class.java) { CollageTemplates.gridFor(1) }
    }

    private fun assertCellsAreInBounds(template: CollageTemplate) {
        template.cells.forEach { cell ->
            assertTrue("${template.id} left", cell.left >= 0f)
            assertTrue("${template.id} top", cell.top >= 0f)
            assertTrue("${template.id} width", cell.width > 0f)
            assertTrue("${template.id} height", cell.height > 0f)
            assertTrue("${template.id} right", cell.left + cell.width <= 1f + epsilon)
            assertTrue("${template.id} bottom", cell.top + cell.height <= 1f + epsilon)
        }
    }

    private fun assertCellsDoNotOverlap(template: CollageTemplate) {
        template.cells.forEachIndexed { index, a ->
            template.cells.drop(index + 1).forEach { b ->
                val overlapWidth = minOf(a.left + a.width, b.left + b.width) - maxOf(a.left, b.left)
                val overlapHeight = minOf(a.top + a.height, b.top + b.height) - maxOf(a.top, b.top)
                assertTrue(
                    "${template.id} cells $a and $b overlap",
                    overlapWidth <= epsilon || overlapHeight <= epsilon,
                )
            }
        }
    }
}
