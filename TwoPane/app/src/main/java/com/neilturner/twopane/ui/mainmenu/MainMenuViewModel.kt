package com.neilturner.twopane.ui.mainmenu

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Settings
import androidx.lifecycle.ViewModel
import com.neilturner.twopane.data.MainMenuItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainMenuViewModel : ViewModel() {
    private val _selectedItemId = MutableStateFlow<String?>("media")
    val selectedItemId: StateFlow<String?> = _selectedItemId.asStateFlow()

    fun setSelectedItem(id: String) {
        _selectedItemId.value = id
    }

    private val _items = MutableStateFlow(
        listOf(
            MainMenuItem(
                id = "media",
                title = "Media",
                subtitle = "Browse and manage media",
                icon = Icons.Default.PermMedia
            ),
            MainMenuItem(
                id = "settings",
                title = "Settings",
                subtitle = "App preferences",
                icon = Icons.Default.Settings
            ),
            MainMenuItem(
                id = "about",
                title = "About",
                subtitle = "Version and information",
                icon = Icons.Default.Info
            )
        )
    )

    val items: StateFlow<List<MainMenuItem>> = _items.asStateFlow()
}
