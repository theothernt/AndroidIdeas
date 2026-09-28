package com.neilturner.inputtest.ui.examples.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import com.neilturner.inputtest.ui.components.ExampleSection

/**
 * Demonstrates the state based API. There is no value / onValueChange pair here: the
 * TextFieldState owns the text, the selection and an undo history, so the field itself
 * does not need to recompose on every keystroke.
 */
@Composable
fun StateBasedTextFieldExample(modifier: Modifier = Modifier) {
	val codeState = rememberTextFieldState()

	// Output transformations change what is displayed without changing what is stored.
	val upperCaseOutput = remember {
		OutputTransformation {
			val original = originalText.toString()
			val upperCased = original.uppercase()
			if (original != upperCased) {
				replace(0, original.length, upperCased)
			}
		}
	}

	ExampleSection(
		title = "TextFieldState (state based API)",
		description = "InputTransformation filters what may be typed, OutputTransformation " +
			"changes what is displayed. Both are chained here to strip non-digits and cap " +
			"the length.",
		modifier = modifier
	) {
		TextField(
			state = codeState,
			label = { Text("Digits only, max 6, shown in caps") },
			lineLimits = TextFieldLineLimits.SingleLine,
			inputTransformation = InputTransformation
				.maxLength(6)
				.byValue { newValue, _ -> newValue.filter { it.isDigit() } },
			outputTransformation = upperCaseOutput,
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
			modifier = Modifier.fillMaxWidth()
		)

		Text(
			text = "Stored value: \"${codeState.text}\"",
			style = MaterialTheme.typography.bodySmall,
			color = Color.Gray
		)
	}
}
