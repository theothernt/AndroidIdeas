package com.neilturner.twopane.ui.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Settings
import androidx.lifecycle.ViewModel
import com.neilturner.twopane.data.SettingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val mobileItems: List<SettingItem> = emptyList(),
    val tvItems: List<SettingItem> = emptyList(),
    val selectedItem: SettingItem? = null,
    val isSubtitlesEnabled: Boolean = true
)

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Initialize data
        val items = listOf(
            SettingItem("media_sources", "Media Sources", "Manage your media sources", Icons.Default.PermMedia),
            SettingItem("overlays", "Overlays", "Configure overlay settings", Icons.Default.Layers),
            SettingItem("appearance", "Appearance", "Customize the look and feel", Icons.Default.Palette),
            SettingItem("playlist", "Playlist", "Manage playlist settings", Icons.AutoMirrored.Filled.List),
            SettingItem("other", "Other", "Advanced settings", Icons.Default.Settings)
        )

        _uiState.update { 
            it.copy(
                mobileItems = items,
                tvItems = items
            ) 
        }
    }

    fun selectItem(item: SettingItem?) {
        _uiState.update { it.copy(selectedItem = item) }
    }
    
    fun selectTvItem(index: Int) {
         val item = _uiState.value.tvItems.getOrNull(index)
         _uiState.update { it.copy(selectedItem = item) }
    }

    fun toggleSubtitles(enabled: Boolean) {
        _uiState.update { it.copy(isSubtitlesEnabled = enabled) }
    }
}
