package com.neilturner.inputtest.ui.examples.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.neilturner.inputtest.ui.components.ExampleSection

@Composable
fun SecureTextFieldExample(
	password: String,
	onPasswordChange: (String) -> Unit,
	passwordVisible: Boolean,
	onTogglePasswordVisibility: () -> Unit,
	modifier: Modifier = Modifier
) {
	ExampleSection(
		title = "SecureTextField and VisualTransformation",
		description = "SecureTextField is the state based equivalent for password style entry. " +
			"The older value / onValueChange style hides text with a VisualTransformation " +
			"instead.",
		modifier = modifier
	) {
		// State based, secure by design
		SecureTextField(
			state = rememberTextFieldState(),
			label = { Text("Password (SecureTextField)") },
			modifier = Modifier.fillMaxWidth()
		)

		// Older style, masked with a VisualTransformation
		OutlinedTextField(
			value = password,
			onValueChange = onPasswordChange,
			label = { Text("Password (VisualTransformation)") },
			singleLine = true,
			visualTransformation = if (passwordVisible) {
				VisualTransformation.None
			} else {
				PasswordVisualTransformation()
			},
			trailingIcon = {
				TextButton(onClick = onTogglePasswordVisibility) {
					Text(if (passwordVisible) "Hide" else "Show")
				}
			},
			modifier = Modifier.fillMaxWidth()
		)
	}
}
