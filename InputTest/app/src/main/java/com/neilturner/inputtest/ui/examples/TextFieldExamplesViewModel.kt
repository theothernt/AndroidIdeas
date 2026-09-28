package com.neilturner.inputtest.ui.examples

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Holds the state for the examples that use the older value / onValueChange style.
 * The examples built on TextFieldState own their state with rememberTextFieldState()
 * instead, which is the point they are demonstrating.
 */
class TextFieldExamplesViewModel : ViewModel() {
	var fullName by mutableStateOf("")
	var company by mutableStateOf("")
	var customStyled by mutableStateOf("")

	var legacyPassword by mutableStateOf("")
	var legacyPasswordVisible by mutableStateOf(false)

	var searchQuery by mutableStateOf("")
	var searchExpanded by mutableStateOf(false)

	val searchSuggestions = listOf("Compose", "Material 3", "TextField", "SearchBar")
}
