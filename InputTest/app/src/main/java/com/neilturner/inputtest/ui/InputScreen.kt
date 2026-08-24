package com.neilturner.inputtest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neilturner.inputtest.ui.theme.InputTestTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun InputScreen(modifier: Modifier = Modifier) {
	var username by remember { mutableStateOf("") }
	var password by remember { mutableStateOf("") }
	val focusManager = LocalFocusManager.current

	Surface(
		modifier = modifier.fillMaxSize(),
		shape = RectangleShape
	) {
		Column(
			modifier = Modifier.padding(32.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {

			OutlinedTextField(
				value = username,
				onValueChange = { username = it },
				label = { Text("Username") },
				singleLine = true,
				colors = OutlinedTextFieldDefaults.colors(
					focusedTextColor = Color.White,
					unfocusedTextColor = Color.White,
					focusedLabelColor = Color.White,
					unfocusedLabelColor = Color.White,
					cursorColor = Color.White,
					focusedBorderColor = Color.White,
					unfocusedBorderColor = Color.Gray
				),
				modifier = Modifier
					.fillMaxWidth()
					.onPreviewKeyEvent { event ->
						if (event.type == KeyEventType.KeyUp &&
							(event.key == Key.Back || event.key == Key.Escape)
						) {
							focusManager.clearFocus()   // drop out of the field, stay on screen
							true                        // consume it so it doesn't also trigger system back
						} else {
							false
						}
					}
			)

			OutlinedTextField(
				value = password,
				onValueChange = { password = it },
				label = { Text("Password") },
				singleLine = true,
				visualTransformation = PasswordVisualTransformation(),
				keyboardOptions = KeyboardOptions(
					keyboardType = KeyboardType.Password,
					imeAction = ImeAction.Next
				),
				colors = OutlinedTextFieldDefaults.colors(
					focusedTextColor = Color.White,
					unfocusedTextColor = Color.White,
					focusedLabelColor = Color.White,
					unfocusedLabelColor = Color.White,
					cursorColor = Color.White,
					focusedBorderColor = Color.White,
					unfocusedBorderColor = Color.Gray
				),
				modifier = Modifier
					.fillMaxWidth()
					.onPreviewKeyEvent { event ->
						if (event.type == KeyEventType.KeyDown &&
							(event.key == Key.Enter || event.key == Key.NumPadEnter)
						) {
							focusManager.moveFocus(FocusDirection.Down)  // moves to next field, not "escape"
							true
						} else {
							false
						}
					}
			)
		}
	}
}

@Preview(showBackground = true)
@Composable
fun InputScreenPreview() {
	InputTestTheme {
		InputScreen()
	}
}
