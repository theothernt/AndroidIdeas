package com.neilturner.arrangethings.logic

import com.neilturner.arrangethings.model.Corner
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlaySlot
import com.neilturner.arrangethings.model.OverlayType
import com.neilturner.arrangethings.model.SLOTS_PER_CORNER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class OverlayLogicTest {

	private fun overlay(
		type: OverlayType = OverlayType.Clock,
		corner: Corner = Corner.TopStart,
		order: Int = 0
	) = OverlayConfig(type = type, corner = corner, order = order)

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
		val overlays = setSlot(emptyList(), Corner.TopEnd, 1, OverlayType.Weather)

		assertEquals(1, overlays.size)
		assertEquals(OverlayType.Weather, slotOverlay(overlays, Corner.TopEnd, 1)?.type)
		assertNull(slotOverlay(overlays, Corner.TopEnd, 0))
		assertValid(overlays)
	}

	@Test
	fun `set slot replaces the overlay that was there`() {
		var overlays = setSlot(emptyList(), Corner.TopStart, 0, OverlayType.Clock)
		overlays = setSlot(overlays, Corner.TopStart, 0, OverlayType.Date)

		assertEquals(1, overlays.size)
		assertEquals(OverlayType.Date, slotOverlay(overlays, Corner.TopStart, 0)?.type)
		assertValid(overlays)
	}

	@Test
	fun `set slot with a null type empties the slot`() {
		var overlays = setSlot(emptyList(), Corner.TopStart, 0, OverlayType.Clock)
		overlays = setSlot(overlays, Corner.TopStart, 0, null)

		assertTrue(overlays.isEmpty())
		assertValid(overlays)
	}

	@Test
	fun `set slot leaves the other slots alone`() {
		var overlays = setSlot(emptyList(), Corner.TopStart, 0, OverlayType.Clock)
		overlays = setSlot(overlays, Corner.TopStart, 1, OverlayType.Date)
		overlays = setSlot(overlays, Corner.TopEnd, 0, OverlayType.Weather)
		overlays = setSlot(overlays, Corner.TopStart, 0, null)

		assertEquals(2, overlays.size)
		assertEquals(OverlayType.Date, slotOverlay(overlays, Corner.TopStart, 1)?.type)
		assertEquals(OverlayType.Weather, slotOverlay(overlays, Corner.TopEnd, 0)?.type)
		assertValid(overlays)
	}

	@Test
	fun `every corner has the same number of slots`() {
		for (corner in Corner.entries) {
			for (order in 0 until SLOTS_PER_CORNER) {
				val slot = OverlaySlot(corner, order)
				assertEquals("${corner.name} slot $order", "$corner-${order}".let { slot.key }, slot.key)
				assertNull(slotOverlay(emptyList(), corner, order))
			}
			assertNull(slotOverlay(emptyList(), corner, SLOTS_PER_CORNER))
		}
	}

	@Test
	fun `overlays in corner are ordered by slot`() {
		val overlays = listOf(
			overlay(OverlayType.Date, Corner.TopStart, 1),
			overlay(OverlayType.Clock, Corner.TopStart, 0)
		)

		assertEquals(
			listOf(OverlayType.Clock, OverlayType.Date),
			overlaysInCorner(overlays, Corner.TopStart).map { it.type }
		)
	}

	// --- invariants --------------------------------------------------------------------------

	@Test
	fun `invariant violations are reported for two overlays in one slot`() {
		val broken = listOf(overlay(OverlayType.Clock, Corner.TopStart, 0), overlay(OverlayType.Date, Corner.TopStart, 0))

		val violations = invariantViolations(broken)
		assertTrue(violations.isNotEmpty())
		assertTrue(violations.any { it.contains("slot 0") })
	}

	@Test
	fun `invariant violations are reported for a slot outside the corner`() {
		val broken = listOf(overlay(OverlayType.Clock, Corner.TopStart, 5))

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
			val type = if (random.nextInt(4) == 0) {
				null
			} else {
				OverlayType.entries[random.nextInt(OverlayType.entries.size)]
			}

			overlays = setSlot(overlays, corner, order, type)

			assertValid(overlays)
			assertTrue("step $step produced too many overlays", overlays.size <= SLOTS_PER_CORNER * 4)
		}
	}
}
