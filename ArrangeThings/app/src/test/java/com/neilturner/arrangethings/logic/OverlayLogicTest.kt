package com.neilturner.arrangethings.logic

import com.neilturner.arrangethings.model.Corner
import com.neilturner.arrangethings.model.MAX_OVERLAYS
import com.neilturner.arrangethings.model.MAX_PER_CORNER
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class OverlayLogicTest {

	private fun overlay(
		id: String,
		corner: Corner,
		order: Int = 0,
		type: OverlayType = OverlayType.Clock
	) = OverlayConfig(id = id, type = type, corner = corner, order = order)

	private fun assertValid(overlays: List<OverlayConfig>) {
		assertEquals(
			"Layout invariants violated: ${invariantViolations(overlays)}",
			emptyList<String>(),
			invariantViolations(overlays)
		)
	}

	// --- adjacency ---------------------------------------------------------------------------

	@Test
	fun `adjacency table matches the specification`() {
		val expected = mapOf(
			Corner.TopStart to mapOf(
				MoveDirection.Left to null,
				MoveDirection.Right to Corner.TopEnd,
				MoveDirection.Up to null,
				MoveDirection.Down to Corner.BottomStart
			),
			Corner.TopEnd to mapOf(
				MoveDirection.Left to Corner.TopStart,
				MoveDirection.Right to null,
				MoveDirection.Up to null,
				MoveDirection.Down to Corner.BottomEnd
			),
			Corner.BottomStart to mapOf(
				MoveDirection.Left to null,
				MoveDirection.Right to Corner.BottomEnd,
				MoveDirection.Up to Corner.TopStart,
				MoveDirection.Down to null
			),
			Corner.BottomEnd to mapOf(
				MoveDirection.Left to Corner.BottomStart,
				MoveDirection.Right to null,
				MoveDirection.Up to Corner.TopEnd,
				MoveDirection.Down to null
			)
		)

		for ((corner, directions) in expected) {
			for ((direction, target) in directions) {
				assertEquals(
					"${corner.name} $direction",
					target,
					adjacentCorner(corner, direction)
				)
			}
		}
	}

	@Test
	fun `adjacency never returns the same corner`() {
		for (corner in Corner.entries) {
			for (direction in MoveDirection.entries) {
				assertTrue(adjacentCorner(corner, direction) != corner)
			}
		}
	}

	// --- add ---------------------------------------------------------------------------------

	@Test
	fun `add fills corners in the specified order`() {
		var overlays = emptyList<OverlayConfig>()

		overlays = addOverlay(overlays, OverlayType.Clock, "a")!!
		overlays = addOverlay(overlays, OverlayType.Date, "b")!!
		overlays = addOverlay(overlays, OverlayType.Location, "c")!!
		overlays = addOverlay(overlays, OverlayType.Weather, "d")!!

		// Each corner holds up to two overlays, so the first two additions share TopStart.
		assertEquals(Corner.TopStart, overlays.single { it.id == "a" }.corner)
		assertEquals(Corner.TopStart, overlays.single { it.id == "b" }.corner)
		assertEquals(Corner.TopEnd, overlays.single { it.id == "c" }.corner)
		assertEquals(Corner.TopEnd, overlays.single { it.id == "d" }.corner)
		assertValid(overlays)
	}

	@Test
	fun `add uses every corner before reusing one`() {
		var overlays = emptyList<OverlayConfig>()
		repeat(MAX_OVERLAYS) { index ->
			overlays = addOverlay(overlays, OverlayType.Clock, "id$index")!!
		}

		val expected = listOf(
			Corner.TopStart, Corner.TopStart,
			Corner.TopEnd, Corner.TopEnd,
			Corner.BottomStart, Corner.BottomStart,
			Corner.BottomEnd, Corner.BottomEnd
		)
		assertEquals(expected, overlays.map { it.corner })
		assertValid(overlays)
	}

	@Test
	fun `add stacks a second overlay in the same corner`() {
		var overlays = emptyList<OverlayConfig>()
		overlays = addOverlay(overlays, OverlayType.Clock, "a")!!
		overlays = addOverlay(overlays, OverlayType.Date, "b")!!

		val topStart = overlaysInCorner(overlays, Corner.TopStart)
		assertEquals(2, topStart.size)
		assertEquals(listOf("a", "b"), topStart.map { it.id })
		assertEquals(listOf(0, 1), topStart.map { it.order })
	}

	@Test
	fun `add returns null when every corner is full`() {
		var overlays = emptyList<OverlayConfig>()
		repeat(MAX_OVERLAYS) { index ->
			overlays = addOverlay(overlays, OverlayType.Clock, "id$index")!!
		}

		assertEquals(MAX_OVERLAYS, overlays.size)
		assertNull(firstCornerWithSpace(overlays))
		assertNull(addOverlay(overlays, OverlayType.Clock, "one-too-many"))
		assertValid(overlays)
	}

	// --- move --------------------------------------------------------------------------------

	@Test
	fun `move into a full corner is ignored`() {
		val overlays = listOf(
			overlay("a", Corner.TopStart, 0),
			overlay("b", Corner.TopStart, 1),
			overlay("c", Corner.TopEnd, 0)
		)

		val moved = moveOverlay(overlays, "c", Corner.TopStart)

		assertEquals(overlays, moved)
		assertEquals(Corner.TopEnd, moved.single { it.id == "c" }.corner)
	}

	@Test
	fun `move into the same corner is ignored`() {
		val overlays = listOf(overlay("a", Corner.TopStart, 0))
		assertEquals(overlays, moveOverlay(overlays, "a", Corner.TopStart))
	}

	@Test
	fun `move renumbers both the source and the target corner`() {
		val overlays = listOf(
			overlay("a", Corner.TopStart, 0),
			overlay("b", Corner.TopStart, 1),
			overlay("c", Corner.TopEnd, 0)
		)

		val moved = moveOverlay(overlays, "a", Corner.TopEnd)

		val topStart = overlaysInCorner(moved, Corner.TopStart)
		val topEnd = overlaysInCorner(moved, Corner.TopEnd)

		assertEquals(listOf("b"), topStart.map { it.id })
		assertEquals(listOf(0), topStart.map { it.order })
		assertEquals(listOf("c", "a"), topEnd.map { it.id })
		assertEquals(listOf(0, 1), topEnd.map { it.order })
		assertValid(moved)
	}

	@Test
	fun `move into an empty corner places the overlay at order zero`() {
		val overlays = listOf(overlay("a", Corner.TopStart, 0))
		val moved = moveOverlay(overlays, "a", Corner.BottomEnd)

		assertEquals(Corner.BottomEnd, moved.single { it.id == "a" }.corner)
		assertEquals(0, moved.single { it.id == "a" }.order)
	}

	// --- swap --------------------------------------------------------------------------------

	@Test
	fun `swap flips the two overlays in a corner`() {
		val overlays = listOf(
			overlay("a", Corner.TopStart, 0),
			overlay("b", Corner.TopStart, 1),
			overlay("c", Corner.TopEnd, 0)
		)

		val swapped = swapOrderInCorner(overlays, "a")

		assertEquals(listOf("b", "a"), overlaysInCorner(swapped, Corner.TopStart).map { it.id })
		assertEquals(listOf("c"), overlaysInCorner(swapped, Corner.TopEnd).map { it.id })
		assertValid(swapped)
	}

	@Test
	fun `swap does nothing when the corner holds a single overlay`() {
		val overlays = listOf(overlay("a", Corner.TopStart, 0))
		assertEquals(overlays, swapOrderInCorner(overlays, "a"))
	}

	// --- remove ------------------------------------------------------------------------------

	@Test
	fun `remove renumbers the remaining overlays in the corner`() {
		val overlays = listOf(
			overlay("a", Corner.TopStart, 0),
			overlay("b", Corner.TopStart, 1)
		)

		val removed = removeOverlay(overlays, "a")

		assertEquals(listOf("b"), removed.map { it.id })
		assertEquals(0, removed.single().order)
		assertValid(removed)
	}

	@Test
	fun `remove of an unknown id leaves the layout untouched`() {
		val overlays = listOf(overlay("a", Corner.TopStart, 0))
		assertEquals(overlays, removeOverlay(overlays, "nope"))
	}

	// --- cancel ------------------------------------------------------------------------------

	@Test
	fun `cancel restores the original corner and order`() {
		val original = overlay("a", Corner.TopStart, 0)
		val overlays = listOf(original, overlay("b", Corner.TopStart, 1))

		val moved = moveOverlay(overlays, "a", Corner.BottomEnd)
		assertEquals(Corner.BottomEnd, moved.single { it.id == "a" }.corner)

		val cancelled = cancelMove(moved, original)

		assertEquals(Corner.TopStart, cancelled.single { it.id == "a" }.corner)
		assertEquals(0, cancelled.single { it.id == "a" }.order)
		assertEquals(listOf("a", "b"), overlaysInCorner(cancelled, Corner.TopStart).map { it.id })
		assertValid(cancelled)
	}

	@Test
	fun `cancel restores a second overlay to its original position in the stack`() {
		val original = overlay("b", Corner.TopStart, 1)
		val overlays = listOf(overlay("a", Corner.TopStart, 0), original)

		val moved = moveOverlay(overlays, "b", Corner.BottomEnd)
		val cancelled = cancelMove(moved, original)

		assertEquals(listOf("a", "b"), overlaysInCorner(cancelled, Corner.TopStart).map { it.id })
		assertEquals(listOf(0, 1), overlaysInCorner(cancelled, Corner.TopStart).map { it.order })
		assertValid(cancelled)
	}

	@Test
	fun `cancel falls back to a corner with space when the original corner filled up`() {
		val original = overlay("a", Corner.TopStart, 0)
		// "a" has left TopStart, which now holds two other overlays.
		val overlays = listOf(
			overlay("b", Corner.TopStart, 0),
			overlay("c", Corner.TopStart, 1),
			overlay("a", Corner.BottomEnd, 0)
		)

		val cancelled = cancelMove(overlays, original)

		assertEquals(2, overlaysInCorner(cancelled, Corner.TopStart).size)
		assertNotEquals(Corner.TopStart, cancelled.single { it.id == "a" }.corner)
		assertValid(cancelled)
	}

	// --- invariants --------------------------------------------------------------------------

	@Test
	fun `no corner ever holds more than the maximum`() {
		var overlays = emptyList<OverlayConfig>()
		repeat(MAX_OVERLAYS) { index ->
			overlays = addOverlay(overlays, OverlayType.Clock, "id$index")!!
		}

		for (corner in Corner.entries) {
			assertTrue(overlaysInCorner(overlays, corner).size <= MAX_PER_CORNER)
		}
	}

	@Test
	fun `invariant violations are reported for an overflowing corner`() {
		val broken = listOf(
			overlay("a", Corner.TopStart, 0),
			overlay("b", Corner.TopStart, 1),
			overlay("c", Corner.TopStart, 2)
		)

		val violations = invariantViolations(broken)
		assertTrue(violations.isNotEmpty())
		assertTrue(violations.any { it.contains("TopStart") })
	}

	@Test
	fun `invariant violations are reported for non contiguous orders`() {
		val broken = listOf(overlay("a", Corner.TopStart, 0), overlay("b", Corner.TopStart, 2))
		assertTrue(invariantViolations(broken).isNotEmpty())
	}

	@Test
	fun `invariant violations are reported for duplicate ids`() {
		val broken = listOf(overlay("a", Corner.TopStart, 0), overlay("a", Corner.TopEnd, 0))
		assertTrue(invariantViolations(broken).isNotEmpty())
	}

	// --- fuzz --------------------------------------------------------------------------------

	@Test
	fun `random operations keep the layout valid`() {
		val random = Random(20261019)
		var overlays = emptyList<OverlayConfig>()
		val grabs = mutableMapOf<String, OverlayConfig>()
		var nextId = 0

		repeat(1000) { step ->
			when (random.nextInt(6)) {
				0 -> {
					val id = "id${nextId++}"
					val result = addOverlay(overlays, OverlayType.entries[random.nextInt(OverlayType.entries.size)], id)
					if (result != null) overlays = result
				}

				1 -> {
					if (overlays.isNotEmpty()) {
						val victim = overlays[random.nextInt(overlays.size)]
						overlays = removeOverlay(overlays, victim.id)
						grabs.remove(victim.id)
					}
				}

				2 -> {
					if (overlays.isNotEmpty()) {
						val moved = overlays[random.nextInt(overlays.size)]
						val direction = MoveDirection.entries[random.nextInt(MoveDirection.entries.size)]
						val target = adjacentCorner(moved.corner, direction)
						if (target != null) {
							if (!isCornerFull(overlays, target)) {
								grabs[moved.id] = moved
								overlays = moveOverlay(overlays, moved.id, target)
							}
						}
					}
				}

				3 -> {
					if (overlays.isNotEmpty()) {
						val swapped = overlays[random.nextInt(overlays.size)]
						overlays = swapOrderInCorner(overlays, swapped.id)
					}
				}

				4 -> {
					val id = grabs.keys.firstOrNull()
					if (id != null) {
						val original = grabs.remove(id)!!
						overlays = cancelMove(overlays, original)
					}
				}

				5 -> {
					if (overlays.isNotEmpty()) {
						val moved = overlays[random.nextInt(overlays.size)]
						val direction = MoveDirection.entries[random.nextInt(MoveDirection.entries.size)]
						val target = adjacentCorner(moved.corner, direction)
						if (target != null) overlays = moveOverlay(overlays, moved.id, target)
					}
				}
			}

			assertValid(overlays)
			assertTrue("step $step produced too many overlays", overlays.size <= MAX_OVERLAYS)
		}
	}
}
