package com.neilturner.persistentlist.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.persistentlist.data.FileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(
    private val fileRepository: FileRepository
) : ViewModel() {

    private var highlightingJob: Job? = null

    private val _highlightedIndex = MutableStateFlow<Int?>(null)
    val highlightedIndex: StateFlow<Int?> = _highlightedIndex.asStateFlow()

    private val _isHighlighting = MutableStateFlow(false)
    val isHighlighting: StateFlow<Boolean> = _isHighlighting.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _sambaDurationMillis = MutableStateFlow<Long?>(null)
    val sambaDurationMillis: StateFlow<Long?> = _sambaDurationMillis.asStateFlow()

    private val _dbDurationMillis = MutableStateFlow<Long?>(null)
    val dbDurationMillis: StateFlow<Long?> = _dbDurationMillis.asStateFlow()

    private val _scanSource = MutableStateFlow<String?>(null)
    val scanSource: StateFlow<String?> = _scanSource.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _hasLoadedFiles = MutableStateFlow(false)
    val hasLoadedFiles: StateFlow<Boolean> = _hasLoadedFiles.asStateFlow()

    private val _files = MutableStateFlow<List<String>>(emptyList())
    val files: StateFlow<List<String>> = _files.asStateFlow()

    val viewedCount: StateFlow<Int> = fileRepository.viewedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            loadMoreFiles()
            if (_files.value.isNotEmpty()) {
                _hasLoadedFiles.value = true
                if (_scanSource.value == null) {
                    _scanSource.value = "Database"
                }
            }
        }
    }

    private suspend fun loadMoreFiles() {
        val currentSize = _files.value.size
        val newFiles = fileRepository.getUnviewedBatch(30, currentSize)
        if (newFiles.isNotEmpty()) {
            _files.value += newFiles
        }
    }

    fun loadFromSamba() {
        viewModelScope.launch {
            // Clear list visually
            _hasLoadedFiles.value = false
            _error.value = null
            _scanSource.value = null
            _sambaDurationMillis.value = null
            _dbDurationMillis.value = null
            stopHighlighting()
            _highlightedIndex.value = null
            _files.value = emptyList()
            
            // Wait 1 second
            kotlinx.coroutines.delay(1000)
            
            // Start scanning
            _isScanning.value = true
            
            try {
                val metrics = fileRepository.scanAndSave()
                _scanSource.value = "Samba"
                
                _sambaDurationMillis.value = metrics.sambaDuration
                _dbDurationMillis.value = metrics.dbDuration
                
                // Load first batch
                loadMoreFiles()

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
            _sambaDurationMillis.value = null
            _dbDurationMillis.value = null
            _scanSource.value = null
            _hasLoadedFiles.value = false
            stopHighlighting()
            _highlightedIndex.value = null
            _files.value = emptyList()
        }
    }

    fun startHighlighting() {
        if (_isHighlighting.value) return

        highlightingJob = viewModelScope.launch {
            _isHighlighting.value = true
            
            // Loop while active and files exist
            while (isActive) {
                val currentFiles = _files.value
                if (currentFiles.isEmpty()) {
                    // Try to load more before quitting?
                    loadMoreFiles()
                    if (_files.value.isEmpty()) {
                        stopHighlighting()
                        break
                    }
                }
                
                // Highlight the first item (since list shrinks)
                _highlightedIndex.value = 0
                
                // Wait delay (simulating viewing/processing)
                kotlinx.coroutines.delay(500)
                
                // Mark current item as viewed
                val currentFile = _files.value.firstOrNull()
                if (currentFile != null) {
                    fileRepository.markAsViewed(currentFile)
                    _files.value = _files.value.drop(1)
                    
                    if (_files.value.size <= 5) {
                        loadMoreFiles()
                    }
                }
            }
        }
    }

    fun stopHighlighting() {
        highlightingJob?.cancel()
        _isHighlighting.value = false
        _highlightedIndex.value = null
    }
}
