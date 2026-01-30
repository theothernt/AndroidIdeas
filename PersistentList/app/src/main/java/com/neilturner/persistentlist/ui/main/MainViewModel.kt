package com.neilturner.persistentlist.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.persistentlist.data.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import com.neilturner.persistentlist.data.UserPreferencesRepository

class MainViewModel(
    private val fileRepository: FileRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private var highlightingJob: Job? = null

    private val _highlightedIndex = MutableStateFlow<Int?>(null)
    val highlightedIndex: StateFlow<Int?> = _highlightedIndex.asStateFlow()

    private val _isHighlighting = MutableStateFlow(false)
    val isHighlighting: StateFlow<Boolean> = _isHighlighting.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanDurationMillis = MutableStateFlow<Long?>(null)
    val scanDurationMillis: StateFlow<Long?> = _scanDurationMillis.asStateFlow()

    private val _scanSource = MutableStateFlow<String?>(null)
    val scanSource: StateFlow<String?> = _scanSource.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _hasLoadedFiles = MutableStateFlow(false)
    val hasLoadedFiles: StateFlow<Boolean> = _hasLoadedFiles.asStateFlow()

    val files: StateFlow<List<String>> = fileRepository.allFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadFromSamba() {
        viewModelScope.launch {
            // Clear list visually
            _hasLoadedFiles.value = false
            _error.value = null
            _scanSource.value = null
            _scanDurationMillis.value = null
            
            // Wait 1 second
            kotlinx.coroutines.delay(1000)
            
            // Start scanning
            _isScanning.value = true
            val startTime = System.currentTimeMillis()
            
            try {
                fileRepository.scanAndSave()
                _scanSource.value = "Samba"
                
                val duration = System.currentTimeMillis() - startTime
                _scanDurationMillis.value = duration
                
                // Show files
                _hasLoadedFiles.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isScanning.value = false
            }
        }
    }
    
    fun loadFromDb() {
        viewModelScope.launch {
            // Clear list visually
            _hasLoadedFiles.value = false
            _error.value = null
            _scanSource.value = null
            _scanDurationMillis.value = null
            
            // Wait 1 second
            kotlinx.coroutines.delay(1000)
            
            // Start loading
            _isScanning.value = true
            val startTime = System.currentTimeMillis()
            
            try {
                // Just load from DB (files flow will update automatically)
                _scanSource.value = "Database"
                
                val duration = System.currentTimeMillis() - startTime
                _scanDurationMillis.value = duration
                
                // Show files
                _hasLoadedFiles.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearDb() {
        viewModelScope.launch {
            fileRepository.clearAll()
            _scanDurationMillis.value = null
            _scanSource.value = null
            _hasLoadedFiles.value = false
            stopHighlighting()
            _highlightedIndex.value = null
        }
    }

    fun startHighlighting() {
        if (_isHighlighting.value) return

        highlightingJob = viewModelScope.launch {
            _isHighlighting.value = true
            
            // Resume from saved index or start at 0
            var currentIndex = userPreferencesRepository.getHighlightedIndex()
            if (currentIndex < 0) currentIndex = 0
            
            // If we are already past the end (e.g. file list changed), reset to 0
            val currentFiles = files.value
            if (currentFiles.isNotEmpty() && currentIndex >= currentFiles.size) {
                 currentIndex = 0
            }

            while (isActive && currentFiles.isNotEmpty()) {
                _highlightedIndex.value = currentIndex
                userPreferencesRepository.saveHighlightedIndex(currentIndex)
                
                kotlinx.coroutines.delay(500)
                
                currentIndex++
                if (currentIndex >= currentFiles.size) {
                    currentIndex = 0
                }
            }
        }
    }

    fun stopHighlighting() {
        highlightingJob?.cancel()
        _isHighlighting.value = false
    }
}
