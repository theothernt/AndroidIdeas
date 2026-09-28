package com.neilturner.inputtest.ui.examples.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.neilturner.inputtest.ui.components.ExampleSection
import com.neilturner.inputtest.ui.theme.Purple80

@Composable
fun BasicTextFieldExample(
	value: String,
	onValueChange: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	ExampleSection(
		title = "BasicTextField (custom decoration)",
		description = "No container, border or label of its own. You draw the decoration and " +
			"place the inner text field wherever you want inside it.",
		modifier = modifier
	) {
		BasicTextField(
			value = value,
			onValueChange = onValueChange,
			singleLine = true,
			textStyle = TextStyle(color = Color.White),
			cursorBrush = SolidColor(Purple80),
			decorationBox = { innerTextField ->
				Column(
					modifier = Modifier.fillMaxWidth(),
					verticalArrangement = Arrangement.spacedBy(6.dp)
				) {
					Text(
						text = "Pill shaped, custom built",
						style = MaterialTheme.typography.labelMedium,
						color = Color.Gray
					)
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.border(
								width = 2.dp,
								color = Purple80,
								shape = RoundedCornerShape(28.dp)
							)
							.padding(horizontal = 20.dp, vertical = 14.dp)
					) {
						if (value.isEmpty()) {
							Text("Type something...", color = Color.Gray)
						}
						innerTextField()
					}
				}
			},
			modifier = Modifier.fillMaxWidth()
		)
	}
}
