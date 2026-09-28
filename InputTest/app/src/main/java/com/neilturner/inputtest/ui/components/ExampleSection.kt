package com.neilturner.inputtest.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.neilturner.inputtest.ui.theme.Purple80

/**
 * A titled block with a short explanation, used to group the examples on the
 * text field examples screen.
 */
@Composable
fun ExampleSection(
	title: String,
	description: String,
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit
) {
	Column(
		modifier = modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(10.dp)
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.titleMedium,
			color = Purple80
		)
		Text(
			text = description,
			style = MaterialTheme.typography.bodySmall,
			color = Color.Gray
		)
		content()
	}
}
