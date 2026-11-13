package com.neilturner.visualmashup.data

import kotlin.math.ceil
import kotlin.math.sqrt

private const val THIRD = 1f / 3f
private const val TWO_THIRDS = 2f / 3f

/**
 * A small library of full-bleed templates. Every template's cells tile the whole canvas, so there
 * is never empty space; images are cropped to fill their cell.
 *
 * Templates are grouped by image count and chosen by [CollageLayouts], which also picks the image
 * order that crops the least.
 */
object CollageTemplates {

    private val twoSideBySide = CollageTemplate(
        id = "two_side_by_side",
        name = "Side by side",
        cells = listOf(
            CellRect(0f, 0f, 0.5f, 1f),
            CellRect(0.5f, 0f, 0.5f, 1f),
        ),
    )

    private val twoStacked = CollageTemplate(
        id = "two_stacked",
        name = "Stacked",
        cells = listOf(
            CellRect(0f, 0f, 1f, 0.5f),
            CellRect(0f, 0.5f, 1f, 0.5f),
        ),
    )

    private val twoHeroLeft = CollageTemplate(
        id = "two_hero_left",
        name = "Hero left",
        cells = listOf(
            CellRect(0f, 0f, 0.6f, 1f),
            CellRect(0.6f, 0f, 0.4f, 1f),
        ),
    )

    private val twoHeroTop = CollageTemplate(
        id = "two_hero_top",
        name = "Hero top",
        cells = listOf(
            CellRect(0f, 0f, 1f, 0.6f),
            CellRect(0f, 0.6f, 1f, 0.4f),
        ),
    )

    private val threeColumns = CollageTemplate(
        id = "three_columns",
        name = "Three columns",
        cells = listOf(
            CellRect(0f, 0f, THIRD, 1f),
            CellRect(THIRD, 0f, THIRD, 1f),
            CellRect(TWO_THIRDS, 0f, THIRD, 1f),
        ),
    )

    private val threeRows = CollageTemplate(
        id = "three_rows",
        name = "Three rows",
        cells = listOf(
            CellRect(0f, 0f, 1f, THIRD),
            CellRect(0f, THIRD, 1f, THIRD),
            CellRect(0f, TWO_THIRDS, 1f, THIRD),
        ),
    )

    private val threeHeroLeft = CollageTemplate(
        id = "three_hero_left",
        name = "Hero left",
        cells = listOf(
            CellRect(0f, 0f, 0.6f, 1f),
            CellRect(0.6f, 0f, 0.4f, 0.5f),
            CellRect(0.6f, 0.5f, 0.4f, 0.5f),
        ),
    )

    private val threeHeroTop = CollageTemplate(
        id = "three_hero_top",
        name = "Hero top",
        cells = listOf(
            CellRect(0f, 0f, 1f, 0.6f),
            CellRect(0f, 0.6f, 0.5f, 0.4f),
            CellRect(0.5f, 0.6f, 0.5f, 0.4f),
        ),
    )

    private val fourGrid = CollageTemplate(
        id = "four_grid",
        name = "2 x 2 grid",
        cells = listOf(
            CellRect(0f, 0f, 0.5f, 0.5f),
            CellRect(0.5f, 0f, 0.5f, 0.5f),
            CellRect(0f, 0.5f, 0.5f, 0.5f),
            CellRect(0.5f, 0.5f, 0.5f, 0.5f),
        ),
    )

    private val fourColumns = CollageTemplate(
        id = "four_columns",
        name = "Four columns",
        cells = listOf(
            CellRect(0f, 0f, 0.25f, 1f),
            CellRect(0.25f, 0f, 0.25f, 1f),
            CellRect(0.5f, 0f, 0.25f, 1f),
            CellRect(0.75f, 0f, 0.25f, 1f),
        ),
    )

    private val fourHeroLeft = CollageTemplate(
        id = "four_hero_left",
        name = "Hero left",
        cells = listOf(
            CellRect(0f, 0f, 0.6f, 1f),
            CellRect(0.6f, 0f, 0.4f, THIRD),
            CellRect(0.6f, THIRD, 0.4f, THIRD),
            CellRect(0.6f, TWO_THIRDS, 0.4f, THIRD),
        ),
    )

    private val fourHeroTop = CollageTemplate(
        id = "four_hero_top",
        name = "Hero top",
        cells = listOf(
            CellRect(0f, 0f, 1f, 0.6f),
            CellRect(0f, 0.6f, THIRD, 0.4f),
            CellRect(THIRD, 0.6f, THIRD, 0.4f),
            CellRect(TWO_THIRDS, 0.6f, THIRD, 0.4f),
        ),
    )

    private val fiveHeroLeft = CollageTemplate(
        id = "five_hero_left",
        name = "Hero left",
        cells = listOf(
            CellRect(0f, 0f, 0.6f, 1f),
            CellRect(0.6f, 0f, 0.2f, 0.5f),
            CellRect(0.8f, 0f, 0.2f, 0.5f),
            CellRect(0.6f, 0.5f, 0.2f, 0.5f),
            CellRect(0.8f, 0.5f, 0.2f, 0.5f),
        ),
    )

    private val fiveHeroTop = CollageTemplate(
        id = "five_hero_top",
        name = "Hero top",
        cells = listOf(
            CellRect(0f, 0f, 1f, 0.6f),
            CellRect(0f, 0.6f, 0.25f, 0.4f),
            CellRect(0.25f, 0.6f, 0.25f, 0.4f),
            CellRect(0.5f, 0.6f, 0.25f, 0.4f),
            CellRect(0.75f, 0.6f, 0.25f, 0.4f),
        ),
    )

    private val sixGrid2x3 = CollageTemplate(
        id = "six_grid_2x3",
        name = "2 x 3 grid",
        cells = listOf(
            CellRect(0f, 0f, THIRD, 0.5f),
            CellRect(THIRD, 0f, THIRD, 0.5f),
            CellRect(TWO_THIRDS, 0f, THIRD, 0.5f),
            CellRect(0f, 0.5f, THIRD, 0.5f),
            CellRect(THIRD, 0.5f, THIRD, 0.5f),
            CellRect(TWO_THIRDS, 0.5f, THIRD, 0.5f),
        ),
    )

    private val sixGrid3x2 = CollageTemplate(
        id = "six_grid_3x2",
        name = "3 x 2 grid",
        cells = listOf(
            CellRect(0f, 0f, 0.5f, THIRD),
            CellRect(0.5f, 0f, 0.5f, THIRD),
            CellRect(0f, THIRD, 0.5f, THIRD),
            CellRect(0.5f, THIRD, 0.5f, THIRD),
            CellRect(0f, TWO_THIRDS, 0.5f, THIRD),
            CellRect(0.5f, TWO_THIRDS, 0.5f, THIRD),
        ),
    )

    private val all = listOf(
        twoSideBySide,
        twoStacked,
        twoHeroLeft,
        twoHeroTop,
        threeColumns,
        threeRows,
        threeHeroLeft,
        threeHeroTop,
        fourGrid,
        fourColumns,
        fourHeroLeft,
        fourHeroTop,
        fiveHeroLeft,
        fiveHeroTop,
        sixGrid2x3,
        sixGrid3x2,
    )

    fun forCount(count: Int): List<CollageTemplate> = all.filter { it.imageCount == count }

    /**
     * A best-effort grid for counts without a dedicated template. The last row may be partly empty,
     * but the collage still uses every supplied photo.
     */
    fun gridFor(count: Int): CollageTemplate {
        require(count >= 2) { "A collage needs at least two images" }
        val cols = ceil(sqrt(count.toDouble())).toInt()
        val rows = ceil(count.toDouble() / cols).toInt()
        val cells = (0 until count).map { index ->
            val col = index % cols
            val row = index / cols
            CellRect(
                left = col.toFloat() / cols,
                top = row.toFloat() / rows,
                width = 1f / cols,
                height = 1f / rows,
            )
        }
        return CollageTemplate(id = "grid_$count", name = "Grid", cells = cells)
    }
}