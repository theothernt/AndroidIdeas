package com.neilturner.inputtest.ui.examples.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.neilturner.inputtest.ui.components.ExampleSection

@Composable
fun OutlinedTextFieldExample(
	value: String,
	onValueChange: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	ExampleSection(
		title = "OutlinedTextField (Material 3)",
		description = "Same API as TextField with an outlined container instead of a filled one. " +
			"Useful on top of busy or image backgrounds.",
		modifier = modifier
	) {
		OutlinedTextField(
			value = value,
			onValueChange = onValueChange,
			label = { Text("Company") },
			placeholder = { Text("Analytical Engines Ltd") },
			singleLine = true,
			modifier = Modifier.fillMaxWidth()
		)
	}
}
