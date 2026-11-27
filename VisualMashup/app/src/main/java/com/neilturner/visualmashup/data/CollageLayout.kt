package com.neilturner.visualmashup.data

import kotlin.math.min

/** A cell in a collage, expressed as fractions of the canvas (0..1). */
data class CellRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * A full-bleed collage layout. A collage always uses at least two photos, and its cells tile the
 * whole canvas, so every pixel is covered; images are cropped to fill their cell.
 */
data class CollageTemplate(
    val id: String,
    val name: String,
    val cells: List<CellRect>,
) {
    val imageCount: Int get() = cells.size
}

/** A template plus the image assigned to each cell, ordered to match [CollageTemplate.cells]. */
data class CollagePlacement(
    val template: CollageTemplate,
    val images: List<SourceImage>,
    /** Fraction of the displayed area that is real image content; `1 - this` is the crop. */
    val visibleAreaFraction: Float,
)

/**
 * Chooses a full-bleed collage for a set of images.
 *
 * Every candidate template is scored by how much of each image survives `ContentScale.Crop`,
 * weighted by cell area, and the best template/image order wins. Cropping is unavoidable for
 * full-bleed layouts, so the goal is to keep it as low as possible.
 */
object CollageLayouts {

    const val CANVAS_ASPECT = 16f / 9f

    /** Solved placements, keyed by the exact image set; the result is deterministic. */
    private val memo = HashMap<List<SourceImage>, CollagePlacement>()

    fun bestFor(images: List<SourceImage>): CollagePlacement {
        require(images.size >= 2) { "A collage needs at least two images" }
        memo[images]?.let { return it }

        val templates = CollageTemplates.forCount(images.size)
            .ifEmpty { listOf(CollageTemplates.gridFor(images.size)) }

        var best: CollagePlacement? = null
        for (template in templates) {
            for (order in permutations(images)) {
                val visible = visibleArea(template, order)
                if (best == null || visible > best.visibleAreaFraction) {
                    best = CollagePlacement(
                        template = template,
                        images = order,
                        visibleAreaFraction = visible,
                    )
                }
            }
        }

        return best!!.also { memo[images] = it }
    }

    /**
     * Total visible image area for [images] placed into [template]'s cells. Because the cells tile
     * the canvas, the result is a fraction between 0 and 1.
     */
    private fun visibleArea(template: CollageTemplate, images: List<SourceImage>): Float {
        return template.cells.mapIndexed { index, cell ->
            val image = images[index]
            val cellAspect = (cell.width * CANVAS_ASPECT) / cell.height
            val imageAspect = image.width.toFloat() / image.height
            // Fraction of the image that remains after filling the cell.
            val visible = min(cellAspect / imageAspect, imageAspect / cellAspect)
            visible * cell.width * cell.height
        }.sum()
    }

    private fun <T> permutations(items: List<T>): List<List<T>> {
        if (items.size > 6) return listOf(items) // keep the search bounded
        return permutationsImpl(items)
    }

    private fun <T> permutationsImpl(items: List<T>): List<List<T>> {
        if (items.size <= 1) return listOf(items)
        return items.flatMapIndexed { index, item ->
            val rest = items.toMutableList().apply { removeAt(index) }
            permutationsImpl(rest).map { permutation -> listOf(item) + permutation }
        }
    }
}