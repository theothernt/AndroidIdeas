package com.neilturner.arrangethings.logic

import com.neilturner.arrangethings.model.Corner
import com.neilturner.arrangethings.model.MetadataConfig
import com.neilturner.arrangethings.model.MetadataField
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlayHeight
import com.neilturner.arrangethings.model.OverlaySlot
import com.neilturner.arrangethings.model.OverlayWidth
import com.neilturner.arrangethings.model.OverlayVariant
import com.neilturner.arrangethings.model.SLOTS_PER_CORNER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class OverlayLogicTest {

	private fun overlay(
		variant: OverlayVariant = OverlayVariant.Clock,
		corner: Corner = Corner.TopStart,
		order: Int = 0
	) = OverlayConfig(variant = variant, corner = corner, order = order)

	private fun assertValid(overlays: List<OverlayConfig>) {
		assertEquals(
			"Layout invariants violated: ${invariantViolations(overlays)}",
			emptyList<String>(),
			invariantViolations(overlays)
		)
	}

	// --- slots -------------------------------------------------------------------------------

	@Test
	fun `set slot puts an overlay in the slot`() {
		val overlays = setSlot(emptyList(), Corner.TopEnd, 1, OverlayVariant.WeatherNow)

		assertEquals(1, overlays.size)
		assertEquals(OverlayVariant.WeatherNow, slotOverlay(overlays, Corner.TopEnd, 1)?.variant)
		assertNull(slotOverlay(overlays, Corner.TopEnd, 0))
		assertValid(overlays)
	}

	@Test
	fun `set slot replaces the overlay that was there`() {
		var overlays = setSlot(emptyList(), Corner.TopStart, 0, OverlayVariant.Clock)
		overlays = setSlot(overlays, Corner.TopStart, 0, OverlayVariant.Date)

		assertEquals(1, overlays.size)
		assertEquals(OverlayVariant.Date, slotOverlay(overlays, Corner.TopStart, 0)?.variant)
		assertValid(overlays)
	}

	@Test
	fun `set slot with a null variant empties the slot`() {
		var overlays = setSlot(emptyList(), Corner.TopStart, 0, OverlayVariant.Clock)
		overlays = setSlot(overlays, Corner.TopStart, 0, null)

		assertTrue(overlays.isEmpty())
		assertValid(overlays)
	}

	@Test
	fun `set slot leaves the other slots alone`() {
		var overlays = setSlot(emptyList(), Corner.TopStart, 0, OverlayVariant.Clock)
		overlays = setSlot(overlays, Corner.TopStart, 1, OverlayVariant.Date)
		overlays = setSlot(overlays, Corner.TopEnd, 0, OverlayVariant.WeatherNow)
		overlays = setSlot(overlays, Corner.TopStart, 0, null)

		assertEquals(2, overlays.size)
		assertEquals(OverlayVariant.Date, slotOverlay(overlays, Corner.TopStart, 1)?.variant)
		assertEquals(OverlayVariant.WeatherNow, slotOverlay(overlays, Corner.TopEnd, 0)?.variant)
		assertValid(overlays)
	}

	@Test
	fun `every corner has the same number of slots`() {
		for (corner in Corner.entries) {
			for (order in 0 until SLOTS_PER_CORNER) {
				val slot = OverlaySlot(corner, order)
				assertEquals("${corner.name}-$order", slot.key)
				assertNull(slotOverlay(emptyList(), corner, order))
			}
			assertNull(slotOverlay(emptyList(), corner, SLOTS_PER_CORNER))
		}
	}

	@Test
	fun `overlays in corner are ordered by slot`() {
		val overlays = listOf(
			overlay(OverlayVariant.Date, Corner.TopStart, 1),
			overlay(OverlayVariant.Clock, Corner.TopStart, 0)
		)

		assertEquals(
			listOf(OverlayVariant.Clock, OverlayVariant.Date),
			overlaysInCorner(overlays, Corner.TopStart).map { it.variant }
		)
	}

	@Test
	fun `every variant declares a shape`() {
		for (variant in OverlayVariant.entries) {
			assertTrue(variant.title.isNotBlank())
			assertTrue(variant.width in OverlayWidth.entries)
			assertTrue(variant.height in OverlayHeight.entries)
		}
	}

	// --- metadata ----------------------------------------------------------------------------

	@Test
	fun `metadata keeps the configuration it was given`() {
		val config = MetadataConfig(listOf(MetadataField.Iso, MetadataField.Place))
		val overlays = setSlot(emptyList(), Corner.TopStart, 0, OverlayVariant.Metadata, config)

		assertEquals(config, slotOverlay(overlays, Corner.TopStart, 0)?.metadata)
		assertValid(overlays)
	}

	@Test
	fun `metadata without a configuration gets the defaults`() {
		val overlays = setSlot(emptyList(), Corner.TopStart, 0, OverlayVariant.Metadata)

		assertEquals(MetadataConfig.Default, slotOverlay(overlays, Corner.TopStart, 0)?.metadata)
	}

	@Test
	fun `replacing metadata with another variant clears the configuration`() {
		var overlays = setSlot(
			emptyList(),
			Corner.TopStart,
			0,
			OverlayVariant.Metadata,
			MetadataConfig(listOf(MetadataField.Iso))
		)
		overlays = setSlot(overlays, Corner.TopStart, 0, OverlayVariant.Clock)

		assertNull(slotOverlay(overlays, Corner.TopStart, 0)?.metadata)
	}

	@Test
	fun `other variants ignore a configuration they are given`() {
		val overlays = setSlot(
			emptyList(),
			Corner.TopStart,
			0,
			OverlayVariant.Clock,
			MetadataConfig(listOf(MetadataField.Iso))
		)

		assertNull(slotOverlay(overlays, Corner.TopStart, 0)?.metadata)
	}

	@Test
	fun `per slot metadata is independent`() {
		val first = MetadataConfig(listOf(MetadataField.Iso))
		val second = MetadataConfig(listOf(MetadataField.Place))

		var overlays = setSlot(emptyList(), Corner.TopStart, 0, OverlayVariant.Metadata, first)
		overlays = setSlot(overlays, Corner.TopStart, 1, OverlayVariant.Metadata, second)

		assertEquals(first, slotOverlay(overlays, Corner.TopStart, 0)?.metadata)
		assertEquals(second, slotOverlay(overlays, Corner.TopStart, 1)?.metadata)
		assertValid(overlays)
	}

	// --- invariants --------------------------------------------------------------------------

	@Test
	fun `invariant violations are reported for two overlays in one slot`() {
		val broken = listOf(
			overlay(OverlayVariant.Clock, Corner.TopStart, 0),
			overlay(OverlayVariant.Date, Corner.TopStart, 0)
		)

		val violations = invariantViolations(broken)
		assertTrue(violations.isNotEmpty())
		assertTrue(violations.any { it.contains("slot 0") })
	}

	@Test
	fun `invariant violations are reported for a slot outside the corner`() {
		val broken = listOf(overlay(OverlayVariant.Clock, Corner.TopStart, 5))

		val violations = invariantViolations(broken)
		assertTrue(violations.isNotEmpty())
		assertTrue(violations.any { it.contains("outside") })
	}

	@Test
	fun `invariant violations are reported for too many overlays in a corner`() {
		val broken = (0..SLOTS_PER_CORNER).map { overlay(corner = Corner.TopStart, order = it) }

		val violations = invariantViolations(broken)
		assertTrue(violations.isNotEmpty())
		assertTrue(violations.any { it.contains("maximum") })
	}

	// --- fuzz --------------------------------------------------------------------------------

	@Test
	fun `random slot changes keep the layout valid`() {
		val random = Random(20261019)
		var overlays = emptyList<OverlayConfig>()

		repeat(1000) { step ->
			val corner = Corner.entries[random.nextInt(Corner.entries.size)]
			val order = random.nextInt(SLOTS_PER_CORNER)
			val variant = if (random.nextInt(4) == 0) {
				null
			} else {
				OverlayVariant.entries[random.nextInt(OverlayVariant.entries.size)]
			}

			overlays = setSlot(overlays, corner, order, variant)

			assertValid(overlays)
			assertTrue("step $step produced too many overlays", overlays.size <= SLOTS_PER_CORNER * 4)
		}
	}
}
