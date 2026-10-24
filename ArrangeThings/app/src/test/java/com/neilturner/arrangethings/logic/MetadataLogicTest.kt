package com.neilturner.arrangethings.logic

import com.neilturner.arrangethings.model.MetadataConfig
import com.neilturner.arrangethings.model.MetadataField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataLogicTest {

	@Test
	fun `default config holds the five fields in display order`() {
		assertEquals(MetadataField.entries.toList(), MetadataConfig.Default.fields)
	}

	@Test
	fun `selected fields keep the order of the config`() {
		val config = MetadataConfig(
			listOf(MetadataField.Place, MetadataField.Iso, MetadataField.ShutterSpeed)
		)

		assertEquals(
			listOf(MetadataField.Place, MetadataField.Iso, MetadataField.ShutterSpeed),
			selectedFields(config)
		)
	}

	@Test
	fun `an empty config has no selected fields`() {
		assertEquals(emptyList<MetadataField>(), selectedFields(MetadataConfig(emptyList())))
	}

	// --- field toggles -----------------------------------------------------------------------

	@Test
	fun `toggling a field appends it`() {
		val config = MetadataConfig(listOf(MetadataField.Iso))
		val updated = toggleField(config, MetadataField.Place)

		assertEquals(listOf(MetadataField.Iso, MetadataField.Place), updated.fields)
	}

	@Test
	fun `toggling a selected field removes it`() {
		val config = MetadataConfig(listOf(MetadataField.Iso, MetadataField.Place))
		val updated = toggleField(config, MetadataField.Iso)

		assertEquals(listOf(MetadataField.Place), updated.fields)
	}

	@Test
	fun `a removed field is added to the end again`() {
		val config = MetadataConfig(
			listOf(MetadataField.Iso, MetadataField.Aperture, MetadataField.FocalLength)
		)
		val without = toggleField(config, MetadataField.Aperture)
		val restored = toggleField(without, MetadataField.Aperture)

		assertEquals(
			listOf(MetadataField.Iso, MetadataField.FocalLength, MetadataField.Aperture),
			restored.fields
		)
	}

	// --- moves -------------------------------------------------------------------------------

	@Test
	fun `moving a field right swaps it with its neighbour`() {
		val config = MetadataConfig(
			listOf(MetadataField.Iso, MetadataField.Aperture, MetadataField.Place)
		)
		val updated = moveField(config, MetadataField.Iso, 1)

		assertEquals(
			listOf(MetadataField.Aperture, MetadataField.Iso, MetadataField.Place),
			updated.fields
		)
	}

	@Test
	fun `moving a field left swaps it with its neighbour`() {
		val config = MetadataConfig(
			listOf(MetadataField.Iso, MetadataField.Aperture, MetadataField.Place)
		)
		val updated = moveField(config, MetadataField.Place, -1)

		assertEquals(
			listOf(MetadataField.Iso, MetadataField.Place, MetadataField.Aperture),
			updated.fields
		)
	}

	@Test
	fun `moving the first field left changes nothing`() {
		val config = MetadataConfig(listOf(MetadataField.Iso, MetadataField.Place))

		assertEquals(config, moveField(config, MetadataField.Iso, -1))
	}

	@Test
	fun `moving the last field right changes nothing`() {
		val config = MetadataConfig(listOf(MetadataField.Iso, MetadataField.Place))

		assertEquals(config, moveField(config, MetadataField.Place, 1))
	}

	@Test
	fun `moving a field that is not selected changes nothing`() {
		val config = MetadataConfig(listOf(MetadataField.Iso))

		assertEquals(config, moveField(config, MetadataField.Place, 1))
	}

	// --- stored names ------------------------------------------------------------------------

	@Test
	fun `stored names map to their fields`() {
		assertEquals(MetadataField.Iso, metadataFieldFromName("Iso"))
		assertEquals(MetadataField.ShutterSpeed, metadataFieldFromName("ShutterSpeed"))
	}

	@Test
	fun `the old shutter name maps to shutter speed`() {
		assertEquals(MetadataField.ShutterSpeed, metadataFieldFromName("Shutter"))
	}

	@Test
	fun `an unknown stored name maps to nothing`() {
		assertNull(metadataFieldFromName("CameraMake"))
	}

	@Test
	fun `stored names become an ordered config`() {
		val config = metadataConfigFromNames(listOf("Place", "Iso", "Shutter"))

		assertEquals(
			listOf(MetadataField.Place, MetadataField.Iso, MetadataField.ShutterSpeed),
			config.fields
		)
	}

	@Test
	fun `duplicate stored names are shown once`() {
		val config = metadataConfigFromNames(listOf("Iso", "Place", "Iso"))

		assertEquals(listOf(MetadataField.Iso, MetadataField.Place), config.fields)
	}

	@Test
	fun `an empty stored list stays empty`() {
		assertEquals(emptyList<MetadataField>(), metadataConfigFromNames(emptyList()).fields)
	}

	@Test
	fun `a stored list of only removed fields falls back to the defaults`() {
		val config = metadataConfigFromNames(listOf("CameraMake", "Lens", "Coordinates"))

		assertEquals(MetadataConfig.DEFAULT_FIELDS, config.fields)
	}
}
