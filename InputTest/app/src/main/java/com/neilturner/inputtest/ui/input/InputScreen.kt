package com.neilturner.inputtest.ui.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun InputScreen(
	modifier: Modifier = Modifier,
	viewModel: InputViewModel = viewModel()
) {
	val focusManager = LocalFocusManager.current

	Surface(
		modifier = modifier.fillMaxSize(),
		shape = RectangleShape
	) {
		Column(
			modifier = Modifier.padding(32.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {

			// No onPreviewKeyEvent here on purpose. Consuming Back would stop the key
			// reaching the IME (to dismiss the keyboard) and NavDisplay (to pop back to Home).
			OutlinedTextField(
				value = viewModel.username,
				onValueChange = { viewModel.username = it },
				label = { Text("Username") },
				singleLine = true,
				colors = whiteFieldColors(),
				modifier = Modifier.fillMaxWidth()
			)

			OutlinedTextField(
				value = viewModel.password,
				onValueChange = { viewModel.password = it },
				label = { Text("Password") },
				singleLine = true,
				visualTransformation = PasswordVisualTransformation(),
				keyboardOptions = KeyboardOptions(
					keyboardType = KeyboardType.Password,
					imeAction = ImeAction.Next
				),
				colors = whiteFieldColors(),
				modifier = Modifier
					.fillMaxWidth()
					.onPreviewKeyEvent { event ->
						if (event.isEnter()) {
							focusManager.moveFocus(FocusDirection.Down)
							true
						} else {
							false
						}
					}
			)

			OutlinedTextField(
				value = viewModel.email,
				onValueChange = { viewModel.email = it },
				label = { Text("Email") },
				singleLine = true,
				keyboardOptions = KeyboardOptions(
					keyboardType = KeyboardType.Email,
					imeAction = ImeAction.Next
				),
				colors = whiteFieldColors(),
				modifier = Modifier
					.fillMaxWidth()
					.onPreviewKeyEvent { event ->
						if (event.isEnter()) {
							focusManager.moveFocus(FocusDirection.Down)
							true
						} else {
							false
						}
					}
			)

			OutlinedTextField(
				value = viewModel.phone,
				onValueChange = { viewModel.phone = it },
				label = { Text("Phone") },
				singleLine = true,
				keyboardOptions = KeyboardOptions(
					keyboardType = KeyboardType.Phone,
					imeAction = ImeAction.Done
				),
				colors = whiteFieldColors(),
				modifier = Modifier
					.fillMaxWidth()
					.onPreviewKeyEvent { event ->
						if (event.isEnter()) {
							// Last field, so cycle round to the top rather than falling off the end
							focusManager.moveFocus(FocusDirection.Next)
							true
						} else {
							false
						}
					}
			)
		}
	}
}

@Composable
private fun whiteFieldColors() = OutlinedTextFieldDefaults.colors(
	focusedTextColor = Color.White,
	unfocusedTextColor = Color.White,
	focusedLabelColor = Color.White,
	unfocusedLabelColor = Color.White,
	cursorColor = Color.White,
	focusedBorderColor = Color.White,
	unfocusedBorderColor = Color.Gray
)

private fun KeyEvent.isEnter(): Boolean =
	type == KeyEventType.KeyDown && (key == Key.Enter || key == Key.NumPadEnter)
