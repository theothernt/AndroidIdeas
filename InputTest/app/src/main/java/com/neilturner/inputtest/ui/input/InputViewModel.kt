package com.neilturner.inputtest.ui.input

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Holds the text the user has typed. Living in a view model means the values survive
 * configuration changes and navigation away from the screen.
 */
class InputViewModel : ViewModel() {
	var username by mutableStateOf("")
	var password by mutableStateOf("")
	var email by mutableStateOf("")
	var phone by mutableStateOf("")
}
