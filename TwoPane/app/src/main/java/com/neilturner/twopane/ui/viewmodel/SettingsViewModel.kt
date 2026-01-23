package com.neilturner.twopane.ui.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
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
        val mobileItems = listOf(
            SettingItem("network", "Network & Internet", "Wi-Fi, Mobile, Data usage"),
            SettingItem("devices", "Connected devices", "Bluetooth, Cast"),
            SettingItem("apps", "Apps", "Recent apps, Default apps"),
            SettingItem("notifications", "Notifications", "Notification history, Conversations"),
            SettingItem("battery", "Battery", "100%")
        )

        val tvItems = listOf(
            SettingItem("accounts", "Accounts", icon = Icons.Default.AccountCircle),
            SettingItem("about", "About", icon = Icons.Default.Info),
            SettingItem("subtitles", "Subtitles", icon = Icons.Default.Subtitles),
            SettingItem("language", "Language", icon = Icons.Default.Translate),
            SettingItem("history", "Search history", icon = Icons.Default.History),
            SettingItem("help", "Help and Support", icon = Icons.AutoMirrored.Filled.Help)
        )

        _uiState.update { 
            it.copy(
                mobileItems = mobileItems,
                tvItems = tvItems
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
