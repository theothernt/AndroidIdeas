package com.neilturner.inputtest.ui.examples.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.neilturner.inputtest.ui.components.ExampleSection

@Composable
fun FilledTextFieldExample(
	value: String,
	onValueChange: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	ExampleSection(
		title = "TextField (Material 3)",
		description = "The default filled choice. Takes value / onValueChange, with the whole " +
			"state living in the caller.",
		modifier = modifier
	) {
		TextField(
			value = value,
			onValueChange = onValueChange,
			label = { Text("Full name") },
			placeholder = { Text("Ada Lovelace") },
			singleLine = true,
			modifier = Modifier.fillMaxWidth()
		)
	}
}
