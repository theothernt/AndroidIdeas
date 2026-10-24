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
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neilturner.arrangethings.model.MetadataField
import com.neilturner.arrangethings.ui.overlays.MetadataChip

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

/** Background of a focused row. */
private val RowFocus = Color(0x24FFFFFF)

/** The panel shape used across the screen. */
private val PanelShape = RoundedCornerShape(12.dp)

/** The row shape used across the screen. */
private val RowShape = RoundedCornerShape(8.dp)

/** Space between two chips in the preview. */
private val ChipGap = 4.dp

/**
 * The full screen setup of a Metadata overlay.
 *
 * The preview on the left is the size the overlay takes in a slot and is also the reorder control:
 * OK picks a field up, Left and Right move it, and OK drops it again. The list on the right chooses
 * which fields the overlay shows. Done applies the draft to the slot, Back cancels.
 */
@Composable
fun MetadataSetupScreen(
	editor: MetadataEditorState,
	onToggleField: (MetadataField) -> Unit,
	onTogglePickup: (MetadataField) -> Unit,
	onMoveField: (MetadataField, Int) -> Unit,
	onDropPickup: () -> Unit,
	onDefaults: () -> Unit,
	onCancel: () -> Unit,
	onApply: () -> Unit
) {
	val firstChipRequester = remember { FocusRequester() }
	val firstFieldRequester = remember { FocusRequester() }

	LaunchedEffect(editor.slot.key) {
		if (editor.draft.fields.isEmpty()) {
			firstFieldRequester.requestFocus()
		} else {
			firstChipRequester.requestFocus()
		}
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

			Spacer(Modifier.height(14.dp))

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f),
				horizontalArrangement = Arrangement.spacedBy(18.dp)
			) {
				PreviewPanel(
					editor = editor,
					firstChipRequester = firstChipRequester,
					onTogglePickup = onTogglePickup,
					onMoveField = onMoveField,
					onDropPickup = onDropPickup,
					modifier = Modifier
						.weight(1f)
						.fillMaxHeight()
				)

				FieldPanel(
					editor = editor,
					firstFieldRequester = firstFieldRequester,
					onToggleField = onToggleField,
					modifier = Modifier
						.width(350.dp)
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

/** The title, the slot and the number of selected fields, on one line. */
@Composable
private fun SetupHeader(editor: MetadataEditorState) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(text = "Metadata", color = Color.White, fontSize = 26.sp)

		Spacer(Modifier.width(16.dp))

		Text(
			text = "${editor.slot.displayName}   -   " +
				"${editor.selectedCount} of ${MetadataField.entries.size} fields",
			color = MutedText,
			fontSize = 12.sp
		)
	}
}

/** The live copy of the overlay, with a focusable chip for every selected field. */
@Composable
private fun PreviewPanel(
	editor: MetadataEditorState,
	firstChipRequester: FocusRequester,
	onTogglePickup: (MetadataField) -> Unit,
	onMoveField: (MetadataField, Int) -> Unit,
	onDropPickup: () -> Unit,
	modifier: Modifier = Modifier
) {
	val pickedUp = editor.pickedUp

	Panel(modifier = modifier) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically
		) {
			SectionLabel("LIVE PREVIEW")

			Spacer(Modifier.weight(1f))

			if (pickedUp != null) {
				Text(
					text = "MOVING",
					color = Color.White,
					fontSize = 10.sp,
					letterSpacing = 1.sp
				)
			}
		}

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
				if (editor.draft.fields.isEmpty()) {
					Text(
						text = "No fields selected",
						color = MutedText,
						fontSize = 12.sp,
						modifier = Modifier.align(Alignment.Center)
					)
				} else {
					Row(
						modifier = Modifier.fillMaxSize(),
						horizontalArrangement = Arrangement.spacedBy(ChipGap),
						verticalAlignment = Alignment.CenterVertically
					) {
						editor.draft.fields.forEachIndexed { index, field ->
							key(field) {
								PreviewChip(
									field = field,
									pickedUp = pickedUp == field,
									modifier = if (index == 0) {
										Modifier.focusRequester(firstChipRequester)
									} else {
										Modifier
									},
									onTogglePickup = { onTogglePickup(field) },
									onMove = { delta -> onMoveField(field, delta) },
									onDropPickup = onDropPickup
								)
							}
						}
					}
				}
			}
		}

		Spacer(Modifier.height(8.dp))

		Text(
			text = when {
				pickedUp != null ->
					"${pickedUp.title} picked up - Left / Right to move, OK to drop"

				editor.draft.fields.isEmpty() ->
					"Choose a field on the right to show it in the overlay."

				else ->
					"OK picks a field up. Left / Right reorders it, OK drops it."
			},
			color = if (pickedUp != null) Color.White else MutedText,
			fontSize = 11.sp,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
	}
}

/**
 * One chip of the preview.
 *
 * OK picks the chip up, Left and Right move it while it is held, and OK drops it again. Moving the
 * focus out of the preview drops it too.
 */
@Composable
private fun PreviewChip(
	field: MetadataField,
	pickedUp: Boolean,
	modifier: Modifier = Modifier,
	onTogglePickup: () -> Unit,
	onMove: (Int) -> Unit,
	onDropPickup: () -> Unit
) {
	var isFocused by remember { mutableStateOf(false) }

	Box(
		modifier = modifier
			.onFocusChanged { isFocused = it.isFocused }
			.focusable()
			.onPreviewKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

				when (event.key) {
					in ACTIVATION_KEYS -> {
						onTogglePickup()
						true
					}

					Key.DirectionLeft -> if (pickedUp) {
						onMove(-1)
						true
					} else {
						false
					}

					Key.DirectionRight -> if (pickedUp) {
						onMove(1)
						true
					} else {
						false
					}

					Key.DirectionUp, Key.DirectionDown -> {
						if (pickedUp) onDropPickup()
						false
					}

					Key.Back, Key.Escape -> if (pickedUp) {
						onDropPickup()
						true
					} else {
						false
					}

					else -> false
				}
			}
	) {
		MetadataChip(field = field, focused = isFocused, pickedUp = pickedUp)
	}
}

/** The five fields that can be shown, with a tick for each selected one. */
@Composable
private fun FieldPanel(
	editor: MetadataEditorState,
	firstFieldRequester: FocusRequester,
	onToggleField: (MetadataField) -> Unit,
	modifier: Modifier = Modifier
) {
	val fields = MetadataField.entries

	Panel(modifier = modifier) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically
		) {
			SectionLabel("FIELDS")

			Spacer(Modifier.weight(1f))

			Text(
				text = "${editor.selectedCount} of ${fields.size}",
				color = MutedText,
				fontSize = 10.sp
			)
		}

		Spacer(Modifier.height(6.dp))

		fields.forEachIndexed { index, field ->
			FieldRow(
				field = field,
				checked = field in editor.draft.fields,
				modifier = if (index == 0) {
					Modifier.focusRequester(firstFieldRequester)
				} else {
					Modifier
				},
				onClick = { onToggleField(field) }
			)

			if (index != fields.lastIndex) Spacer(Modifier.height(4.dp))
		}

		Spacer(Modifier.height(10.dp))

		Text(
			text = "A selected field is added to the end of the preview. " +
				"Use the preview above to change the order.",
			color = MutedText,
			fontSize = 10.sp,
			maxLines = 2
		)
	}
}

/** One field in the right column, with its demo value. */
@Composable
private fun FieldRow(
	field: MetadataField,
	checked: Boolean,
	modifier: Modifier = Modifier,
	onClick: () -> Unit
) {
	var isFocused by remember { mutableStateOf(false) }

	SelectableRow(
		onClick = onClick,
		modifier = modifier
			.height(40.dp)
			.border(1.dp, if (isFocused) Color.White else Color.Transparent, RowShape)
			.background(if (isFocused) RowFocus else Color.Transparent)
			.onFocusChanged { isFocused = it.isFocused }
	) {
		CheckMark(checked = checked)

		Spacer(Modifier.width(10.dp))

		Text(
			text = field.title,
			color = Color.White,
			fontSize = 13.sp,
			maxLines = 1,
			modifier = Modifier.width(120.dp)
		)

		Text(
			text = field.demoValue,
			color = MutedText,
			fontSize = 12.sp,
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
	content: @Composable RowScope.() -> Unit
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.clip(RowShape)
			.focusable()
			.onPreviewKeyEvent { event ->
				if (event.type == KeyEventType.KeyDown && event.key in ACTIVATION_KEYS) {
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
private fun CheckMark(checked: Boolean) {
	val shape = RoundedCornerShape(4.dp)

	Box(
		modifier = Modifier
			.size(18.dp)
			.clip(shape)
			.background(if (checked) Color.White else Color.Transparent)
			.border(1.dp, Color.White, shape),
		contentAlignment = Alignment.Center
	) {
		if (checked) {
			Text(text = "✓", color = Color.Black, fontSize = 11.sp)
		}
	}
}
