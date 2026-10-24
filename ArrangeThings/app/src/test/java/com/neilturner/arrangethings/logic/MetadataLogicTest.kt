package com.neilturner.arrangethings.logic

import com.neilturner.arrangethings.model.MAX_METADATA_FIELDS
import com.neilturner.arrangethings.model.MetadataCategory
import com.neilturner.arrangethings.model.MetadataConfig
import com.neilturner.arrangethings.model.MetadataField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataLogicTest {

	private fun fieldsOf(category: MetadataCategory): List<MetadataField> =
		MetadataField.entries.filter { it.category == category }

	@Test
	fun `default config holds eight fields`() {
		assertEquals(MAX_METADATA_FIELDS, MetadataConfig.Default.fields.size)
	}

	@Test
	fun `sections are in category order`() {
		val config = MetadataConfig(
			setOf(MetadataField.Place, MetadataField.Iso, MetadataField.CameraMake)
		)

		assertEquals(
			listOf(
				MetadataCategory.Camera,
				MetadataCategory.Exposure,
				MetadataCategory.Location
			),
			metadataSections(config).map { it.category }
		)
	}

	@Test
	fun `sections skip empty categories`() {
		val config = MetadataConfig(setOf(MetadataField.Iso))

		assertEquals(listOf(MetadataCategory.Exposure), metadataSections(config).map { it.category })
	}

	@Test
	fun `sections list their fields in canonical order`() {
		val config = MetadataConfig(
			setOf(
				MetadataField.Shutter,
				MetadataField.Iso,
				MetadataField.Aperture
			)
		)

		assertEquals(
			listOf(MetadataField.Iso, MetadataField.Aperture, MetadataField.Shutter),
			metadataSections(config).single().fields
		)
	}

	@Test
	fun `an empty config has no sections`() {
		assertTrue(metadataSections(MetadataConfig(emptySet())).isEmpty())
	}

	@Test
	fun `selected fields only returns the fields of the category`() {
		val config = MetadataConfig(
			setOf(MetadataField.Iso, MetadataField.Place, MetadataField.CameraMake)
		)

		assertEquals(
			listOf(MetadataField.CameraMake),
			selectedFields(config, MetadataCategory.Camera)
		)
	}

	// --- field toggles -----------------------------------------------------------------------

	@Test
	fun `toggling a field adds it`() {
		val config = MetadataConfig(setOf(MetadataField.CameraMake))
		val updated = toggleField(config, MetadataField.Iso)

		assertEquals(setOf(MetadataField.CameraMake, MetadataField.Iso), updated.fields)
	}

	@Test
	fun `toggling a selected field removes it`() {
		val config = MetadataConfig(setOf(MetadataField.CameraMake, MetadataField.Iso))
		val updated = toggleField(config, MetadataField.CameraMake)

		assertEquals(setOf(MetadataField.Iso), updated.fields)
	}

	@Test
	fun `toggling a field at the limit changes nothing`() {
		val full = MetadataConfig(MetadataField.entries.take(MAX_METADATA_FIELDS).toSet())

		assertEquals(full, toggleField(full, MetadataField.VideoBitrate))
	}

	@Test
	fun `a selected field can still be removed at the limit`() {
		val full = MetadataConfig(MetadataField.entries.take(MAX_METADATA_FIELDS).toSet())
		val updated = toggleField(full, MetadataField.CameraMake)

		assertEquals(MAX_METADATA_FIELDS - 1, updated.fields.size)
	}

	// --- category toggles --------------------------------------------------------------------

	@Test
	fun `toggling an empty category adds all of its fields`() {
		val updated = toggleCategory(MetadataConfig(emptySet()), MetadataCategory.Exposure)

		assertEquals(fieldsOf(MetadataCategory.Exposure).toSet(), updated.fields)
	}

	@Test
	fun `toggling a full category clears it`() {
		val config = MetadataConfig(fieldsOf(MetadataCategory.Camera).toSet())
		val updated = toggleCategory(config, MetadataCategory.Camera)

		assertTrue(updated.fields.isEmpty())
	}

	@Test
	fun `toggling a partial category completes it`() {
		val config = MetadataConfig(setOf(MetadataField.CameraMake))
		val updated = toggleCategory(config, MetadataCategory.Camera)

		assertEquals(fieldsOf(MetadataCategory.Camera).toSet(), updated.fields)
	}

	@Test
	fun `toggling a category stops at the field limit`() {
		val start = setOf(
			MetadataField.Iso,
			MetadataField.Aperture,
			MetadataField.Shutter,
			MetadataField.FocalLength
		)
		val updated = toggleCategory(MetadataConfig(start), MetadataCategory.File)

		assertEquals(MAX_METADATA_FIELDS, updated.fields.size)
		assertTrue(updated.fields.containsAll(start))
		assertTrue(MetadataField.FileName in updated.fields)
		assertTrue(MetadataField.DateTaken !in updated.fields)
	}

	@Test
	fun `toggling a category at the limit changes nothing`() {
		val full = MetadataConfig(MetadataField.entries.take(MAX_METADATA_FIELDS).toSet())

		assertEquals(full, toggleCategory(full, MetadataCategory.Video))
	}

	@Test
	fun `every field belongs to a category that has at least one field`() {
		for (category in MetadataCategory.entries) {
			assertTrue("$category has no fields", fieldsOf(category).isNotEmpty())
		}
	}
}
