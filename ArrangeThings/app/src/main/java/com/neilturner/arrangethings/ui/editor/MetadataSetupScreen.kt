package com.neilturner.arrangethings.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neilturner.arrangethings.logic.selectedFields
import com.neilturner.arrangethings.model.MAX_METADATA_FIELDS
import com.neilturner.arrangethings.model.MetadataCategory
import com.neilturner.arrangethings.model.MetadataConfig
import com.neilturner.arrangethings.model.MetadataField
import com.neilturner.arrangethings.ui.overlays.MetadataContent

/** Background of the whole setup screen. */
private val SetupBackground = Color(0xFF000000)

/** Background of a panel. */
private val PanelBackground = Color(0xFF141414)

/** Background of the overlay preview. */
private val PreviewBackground = Color(0xFF101010)

/** Border of a panel or unchecked box. */
private val PanelBorder = Color(0x33FFFFFF)

/** Colour of secondary text. */
private val MutedText = Color(0xB3FFFFFF)

/** Colour of a row that cannot be changed. */
private val DisabledText = Color(0x4DFFFFFF)

/** Background of a focused row. */
private val RowFocus = Color(0x24FFFFFF)

/** Background of the active category. */
private val RowActive = Color(0x14FFFFFF)

/** The panel shape used across the screen. */
private val PanelShape = RoundedCornerShape(12.dp)

/** The row shape used across the screen. */
private val RowShape = RoundedCornerShape(8.dp)

/**
 * The full screen setup of a Metadata overlay.
 *
 * The preview on the left is the exact size the overlay takes in a slot. The middle column picks a
 * category and the right column lists its fields. Done applies the draft to the slot, Back cancels.
 */
@Composable
fun MetadataSetupScreen(
	editor: MetadataEditorState,
	onShowCategory: (MetadataCategory) -> Unit,
	onToggleField: (MetadataField) -> Unit,
	onToggleCategory: () -> Unit,
	onDefaults: () -> Unit,
	onCancel: () -> Unit,
	onApply: () -> Unit
) {
	val categoryRequester = remember { FocusRequester() }

	LaunchedEffect(editor.slot.key) {
		categoryRequester.requestFocus()
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(SetupBackground)
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 28.dp, vertical = 20.dp)
		) {
			SetupHeader(editor)

			Spacer(Modifier.height(16.dp))

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f),
				horizontalArrangement = Arrangement.spacedBy(18.dp)
			) {
				PreviewPanel(
					config = editor.draft,
					modifier = Modifier
						.weight(1.25f)
						.fillMaxHeight()
				)

				CategoryPanel(
					editor = editor,
					requester = categoryRequester,
					onShowCategory = onShowCategory,
					modifier = Modifier
						.width(190.dp)
						.fillMaxHeight()
				)

				FieldPanel(
					editor = editor,
					onToggleField = onToggleField,
					onToggleCategory = onToggleCategory,
					modifier = Modifier
						.weight(1.1f)
						.fillMaxHeight()
				)
			}

			Spacer(Modifier.height(14.dp))

			Actions(
				onDefaults = onDefaults,
				onCancel = onCancel,
				onApply = onApply
			)
		}
	}
}

/** The title and the count of the selected fields. */
@Composable
private fun SetupHeader(editor: MetadataEditorState) {
	Column(modifier = Modifier.fillMaxWidth()) {
		Text(text = "Metadata", color = Color.White, fontSize = 28.sp)

		Spacer(Modifier.height(2.dp))

		Text(
			text = buildString {
				append(editor.slot.displayName)
				append("   -   ")
				append("${editor.selectedCount} of $MAX_METADATA_FIELDS fields")
				if (editor.isFull) append("   -   limit reached")
			},
			color = MutedText,
			fontSize = 12.sp
		)
	}
}

/** The live copy of the overlay, drawn at the size it takes in a slot. */
@Composable
private fun PreviewPanel(config: MetadataConfig, modifier: Modifier = Modifier) {
	Panel(modifier = modifier) {
		SectionLabel("LIVE PREVIEW")

		Spacer(Modifier.height(8.dp))

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f),
			contentAlignment = Alignment.Center
		) {
			Box(
				modifier = Modifier
					.requiredWidth(300.dp)
					.requiredHeight(128.dp)
					.clip(PanelShape)
					.background(PreviewBackground)
					.border(1.dp, PanelBorder, PanelShape)
					.padding(horizontal = 8.dp, vertical = 6.dp)
			) {
				MetadataContent(config = config, modifier = Modifier.fillMaxSize())
			}
		}
	}
}

/** The fixed categories, with the number of fields each one shows. */
@Composable
private fun CategoryPanel(
	editor: MetadataEditorState,
	requester: FocusRequester,
	onShowCategory: (MetadataCategory) -> Unit,
	modifier: Modifier = Modifier
) {
	Panel(modifier = modifier) {
		SectionLabel("SECTIONS")

		Spacer(Modifier.height(6.dp))

		MetadataCategory.entries.forEach { category ->
			val total = fieldsOf(category).size
			val selected = selectedFields(editor.draft, category).size

			CategoryRow(
				category = category,
				selected = selected,
				total = total,
				active = category == editor.activeCategory,
				modifier = if (category == editor.activeCategory) {
					Modifier.focusRequester(requester)
				} else {
					Modifier
				},
				onClick = { onShowCategory(category) }
			)

			if (category != MetadataCategory.entries.last()) Spacer(Modifier.height(4.dp))
		}
	}
}

/** One category in the middle column. */
@Composable
private fun CategoryRow(
	category: MetadataCategory,
	selected: Int,
	total: Int,
	active: Boolean,
	modifier: Modifier = Modifier,
	onClick: () -> Unit
) {
	var isFocused by remember { mutableStateOf(false) }

	SelectableRow(
		onClick = onClick,
		modifier = modifier
			.height(42.dp)
			.border(
				width = 1.dp,
				color = when {
					isFocused -> Color.White
					active -> Color(0x66FFFFFF)
					else -> Color.Transparent
				},
				shape = RowShape
			)
			.background(
				color = when {
					isFocused -> RowFocus
					active -> RowActive
					else -> Color.Transparent
				}
			)
			.onFocusChanged { isFocused = it.isFocused }
	) {
		Text(
			text = category.title,
			color = if (active) Color.White else MutedText,
			fontSize = 14.sp,
			modifier = Modifier.weight(1f)
		)

		Text(text = "$selected/$total", color = MutedText, fontSize = 11.sp)
	}
}

/** The fields of the active category. */
@Composable
private fun FieldPanel(
	editor: MetadataEditorState,
	onToggleField: (MetadataField) -> Unit,
	onToggleCategory: () -> Unit,
	modifier: Modifier = Modifier
) {
	val fields = fieldsOf(editor.activeCategory)
	val selected = fields.count { it in editor.draft.fields }
	val allSelected = selected == fields.size

	Panel(modifier = modifier) {
		SectionLabel("FIELDS   ${editor.activeCategory.title.uppercase()}")

		Spacer(Modifier.height(6.dp))

		ToggleAllRow(
			title = "All ${editor.activeCategory.title}",
			selected = selected,
			total = fields.size,
			checked = allSelected,
			enabled = allSelected || !editor.isFull,
			onClick = onToggleCategory
		)

		Spacer(Modifier.height(4.dp))

		fields.forEach { field ->
			val checked = field in editor.draft.fields

			FieldRow(
				field = field,
				checked = checked,
				enabled = checked || !editor.isFull,
				onClick = { onToggleField(field) }
			)

			if (field != fields.last()) Spacer(Modifier.height(4.dp))
		}
	}
}

/** The row that selects or clears the whole category. */
@Composable
private fun ToggleAllRow(
	title: String,
	selected: Int,
	total: Int,
	checked: Boolean,
	enabled: Boolean,
	onClick: () -> Unit
) {
	var isFocused by remember { mutableStateOf(false) }
	val textColor = if (enabled) Color.White else DisabledText

	SelectableRow(
		onClick = onClick,
		enabled = enabled,
		modifier = Modifier
			.height(38.dp)
			.border(1.dp, if (isFocused) Color.White else Color.Transparent, RowShape)
			.background(if (isFocused) RowFocus else Color.Transparent)
			.onFocusChanged { isFocused = it.isFocused }
	) {
		CheckMark(checked = checked, enabled = enabled)

		Spacer(Modifier.width(10.dp))

		Text(
			text = title,
			color = textColor,
			fontSize = 12.sp,
			modifier = Modifier.weight(1f)
		)

		Text(text = "$selected/$total", color = MutedText, fontSize = 11.sp)
	}
}

/** One field in the right column, with its demo value. */
@Composable
private fun FieldRow(
	field: MetadataField,
	checked: Boolean,
	enabled: Boolean,
	onClick: () -> Unit
) {
	var isFocused by remember { mutableStateOf(false) }
	val textColor = if (enabled) Color.White else DisabledText
	val valueColor = if (enabled) MutedText else DisabledText

	SelectableRow(
		onClick = onClick,
		enabled = enabled,
		modifier = Modifier
			.height(38.dp)
			.border(1.dp, if (isFocused) Color.White else Color.Transparent, RowShape)
			.background(if (isFocused) RowFocus else Color.Transparent)
			.onFocusChanged { isFocused = it.isFocused }
	) {
		CheckMark(checked = checked, enabled = enabled)

		Spacer(Modifier.width(10.dp))

		Text(
			text = field.label,
			color = textColor,
			fontSize = 12.sp,
			maxLines = 1,
			modifier = Modifier.width(78.dp)
		)

		Text(
			text = field.demoValue,
			color = valueColor,
			fontSize = 13.sp,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f)
		)
	}
}

/** The Defaults, Cancel and Done buttons. */
@Composable
private fun Actions(
	onDefaults: () -> Unit,
	onCancel: () -> Unit,
	onApply: () -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically
	) {
		Spacer(Modifier.weight(1f))

		Button(onClick = onDefaults) {
			Text(text = "Defaults")
		}

		Spacer(Modifier.width(12.dp))

		Button(onClick = onCancel) {
			Text(text = "Cancel")
		}

		Spacer(Modifier.width(12.dp))

		Button(onClick = onApply) {
			Text(text = "Done")
		}
	}
}

/** A panel with a label on top. */
@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
	Column(
		modifier = modifier
			.clip(PanelShape)
			.background(PanelBackground)
			.border(1.dp, PanelBorder, PanelShape)
			.padding(12.dp),
		content = content
	)
}

/** A small uppercase label at the top of a panel. */
@Composable
private fun SectionLabel(text: String) {
	Text(
		text = text,
		color = MutedText,
		fontSize = 10.sp,
		letterSpacing = 1.sp,
		maxLines = 1
	)
}

/** A focusable row that activates on Select. */
@Composable
private fun SelectableRow(
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	content: @Composable RowScope.() -> Unit
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.clip(RowShape)
			.focusable(enabled = enabled)
			.onPreviewKeyEvent { event ->
				if (enabled && event.type == KeyEventType.KeyDown && event.key in ACTIVATION_KEYS) {
					onClick()
					true
				} else {
					false
				}
			}
			.padding(horizontal = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		content = content
	)
}

/** A square tick box, drawn rather than typed so it stays black and white. */
@Composable
private fun CheckMark(checked: Boolean, enabled: Boolean) {
	val color = if (enabled) Color.White else DisabledText
	val shape = RoundedCornerShape(4.dp)

	Box(
		modifier = Modifier
			.size(18.dp)
			.clip(shape)
			.background(if (checked) color else Color.Transparent)
			.border(1.dp, color, shape),
		contentAlignment = Alignment.Center
	) {
		if (checked) {
			Text(text = "✓", color = Color.Black, fontSize = 11.sp)
		}
	}
}

/** The fields of a category, in canonical order. */
private fun fieldsOf(category: MetadataCategory): List<MetadataField> =
	MetadataField.entries.filter { it.category == category }
