package com.neilturner.exifblur.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleSizeTest {

    // The app decodes for a 1080p TV screen.
    private val targetWidth = 1920
    private val targetHeight = 1080

    @Test
    fun `an image already inside the target is not sampled`() {
        assertEquals(1, calculateSampleSize(1920, 1080, targetWidth, targetHeight))
    }

    @Test
    fun `an image smaller than the target is not sampled`() {
        assertEquals(1, calculateSampleSize(640, 480, targetWidth, targetHeight))
    }

    @Test
    fun `a typical 12MP photo is sampled down to roughly the target`() {
        val sampleSize = calculateSampleSize(4032, 3024, targetWidth, targetHeight)

        val decodedWidth = 4032 / sampleSize
        val decodedHeight = 3024 / sampleSize

        // Large enough that the screen is filled without obvious upscaling...
        assertTrue("width $decodedWidth too small", decodedWidth >= targetWidth / 2)
        // ...and not so small that the result is a quarter of what is needed.
        assertTrue("width $decodedWidth needlessly large", decodedWidth <= targetWidth * 2)
        assertTrue(decodedHeight > 0)
    }

    /**
     * The bug this replaced compared the largest source dimension against the
     * largest target. That over-samples tall images, whose width never needed
     * the reduction in the first place.
     */
    @Test
    fun `a tall image is driven by its height not its width`() {
        // Width fits the target already; only height overflows.
        val sampleSize = calculateSampleSize(1920, 10000, targetWidth, targetHeight)

        assertEquals(8, sampleSize)
        assertEquals(240, 1920 / sampleSize)
        assertEquals(1250, 10000 / sampleSize)
    }

    /**
     * The mirror case: a wide panorama is driven by its width, so the old
     * largest-to-largest comparison could not under-sample it either.
     */
    @Test
    fun `a wide panorama is driven by its width`() {
        val sampleSize = calculateSampleSize(10000, 1080, targetWidth, targetHeight)

        // Height already fits, so the loop must be driven by width alone.
        assertEquals(4, sampleSize)
        assertEquals(2500, 10000 / sampleSize)
    }

    @Test
    fun `a square image is sampled to about the target`() {
        assertEquals(2, calculateSampleSize(4000, 4000, targetWidth, targetHeight))
    }

    @Test
    fun `the aspect ratio is preserved by the sampling`() {
        val width = 3396
        val height = 1355
        val sampleSize = calculateSampleSize(width, height, targetWidth, targetHeight)

        // Power-of-two subsampling divides both axes by the same factor, so the
        // aspect ratio is exact. Only integer truncation of an odd dimension
        // (1355 / 2 == 677) introduces a difference, bounded by one pixel per axis.
        val sourceRatio = width.toDouble() / height
        val decodedRatio = (width / sampleSize).toDouble() / (height / sampleSize)

        val tolerance = 1.0 / (width / sampleSize) + 1.0 / (height / sampleSize)
        assertEquals(sourceRatio, decodedRatio, tolerance)
    }

    @Test
    fun `the aspect ratio is exact when both dimensions divide evenly`() {
        val width = 3392
        val height = 1356
        val sampleSize = calculateSampleSize(width, height, targetWidth, targetHeight)

        val sourceRatio = width.toDouble() / height
        val decodedRatio = (width / sampleSize).toDouble() / (height / sampleSize)

        assertEquals(sourceRatio, decodedRatio, 0.0001)
    }

    @Test
    fun `zero target dimensions do not divide by zero`() {
        // A degenerate target must still return a usable power of two rather
        // than throwing on the comparison.
        assertTrue(calculateSampleSize(4000, 3000, 0, 0) >= 1)
    }
}