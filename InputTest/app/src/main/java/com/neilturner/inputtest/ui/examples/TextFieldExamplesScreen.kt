package com.neilturner.inputtest.ui.examples

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.neilturner.inputtest.ui.examples.components.BasicTextFieldExample
import com.neilturner.inputtest.ui.examples.components.FilledTextFieldExample
import com.neilturner.inputtest.ui.examples.components.OutlinedTextFieldExample
import com.neilturner.inputtest.ui.examples.components.SearchBarExample
import com.neilturner.inputtest.ui.examples.components.SecureTextFieldExample
import com.neilturner.inputtest.ui.examples.components.StateBasedTextFieldExample
import com.neilturner.inputtest.ui.theme.ComposeMaterial3Theme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TextFieldExamplesScreen(
	modifier: Modifier = Modifier,
	viewModel: TextFieldExamplesViewModel = viewModel()
) {
	Surface(
		modifier = modifier.fillMaxSize(),
		shape = RectangleShape
	) {
		ComposeMaterial3Theme {
			Column(
				modifier = Modifier
					.padding(32.dp)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(28.dp)
			) {
				Text(
					text = "Text Field Examples",
					style = MaterialTheme.typography.headlineLarge
				)

				FilledTextFieldExample(
					value = viewModel.fullName,
					onValueChange = { viewModel.fullName = it }
				)

				OutlinedTextFieldExample(
					value = viewModel.company,
					onValueChange = { viewModel.company = it }
				)

				BasicTextFieldExample(
					value = viewModel.customStyled,
					onValueChange = { viewModel.customStyled = it }
				)

				StateBasedTextFieldExample()

				SecureTextFieldExample(
					password = viewModel.legacyPassword,
					onPasswordChange = { viewModel.legacyPassword = it },
					passwordVisible = viewModel.legacyPasswordVisible,
					onTogglePasswordVisibility = {
						viewModel.legacyPasswordVisible = !viewModel.legacyPasswordVisible
					}
				)

				SearchBarExample(
					query = viewModel.searchQuery,
					onQueryChange = { viewModel.searchQuery = it },
					expanded = viewModel.searchExpanded,
					onExpandedChange = { viewModel.searchExpanded = it },
					suggestions = viewModel.searchSuggestions,
					onSuggestionSelected = { suggestion ->
						viewModel.searchQuery = suggestion
						viewModel.searchExpanded = false
					}
				)
			}
		}
	}
}
