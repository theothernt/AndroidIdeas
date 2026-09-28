@file:OptIn(ExperimentalMaterial3Api::class)

package com.neilturner.inputtest.ui.examples.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neilturner.inputtest.ui.components.ExampleSection

@Composable
fun SearchBarExample(
	query: String,
	onQueryChange: (String) -> Unit,
	expanded: Boolean,
	onExpandedChange: (Boolean) -> Unit,
	suggestions: List<String>,
	onSuggestionSelected: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	val matches = suggestions.filter { it.contains(query, ignoreCase = true) }

	ExampleSection(
		title = "SearchBar (Material 3)",
		description = "The Material 3 search component. It expands to fill the space and " +
			"hosts its own content, with a DockedSearchBar as the docked alternative.",
		modifier = modifier
	) {
		SearchBar(
			inputField = {
				SearchBarDefaults.InputField(
					query = query,
					onQueryChange = onQueryChange,
					onSearch = { onExpandedChange(false) },
					expanded = expanded,
					onExpandedChange = onExpandedChange,
					placeholder = { Text("Search") }
				)
			},
			expanded = expanded,
			onExpandedChange = onExpandedChange,
			shape = RoundedCornerShape(16.dp),
			modifier = Modifier.fillMaxWidth()
		) {
			// Shown inside the expanded search bar
			Text(
				text = if (matches.isEmpty()) "No matches" else "Suggestions",
				style = MaterialTheme.typography.titleSmall,
				color = Color.Gray,
				modifier = Modifier.padding(16.dp)
			)
			matches.forEach { suggestion ->
				TextButton(
					onClick = { onSuggestionSelected(suggestion) },
					modifier = Modifier.fillMaxWidth()
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.Start
					) {
						Text(suggestion)
					}
				}
			}
		}
	}
}
