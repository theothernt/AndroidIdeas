package com.neilturner.persistentlist.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.persistentlist.data.SmbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val smbRepository: SmbRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Initial)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun scanFiles() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            val startTime = System.currentTimeMillis()
            try {
                val files = smbRepository.listFiles()
                val duration = System.currentTimeMillis() - startTime
                _uiState.value = MainUiState.Success(files, duration)
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed interface MainUiState {
    data object Initial : MainUiState
    data object Loading : MainUiState
    data class Success(val files: List<Uri>, val scanDurationMillis: Long) : MainUiState
    data class Error(val message: String) : MainUiState
}