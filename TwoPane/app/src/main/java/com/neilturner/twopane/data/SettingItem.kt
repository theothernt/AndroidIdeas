package com.neilturner.twopane.data

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents a setting item in the application.
 *
 * @property id Unique identifier for the setting item (can be the title if unique).
 * @property title Display title of the setting.
 * @property description Subtitle or description (primarily for mobile).
 * @property icon Icon for the setting (primarily for TV).
 */
data class SettingItem(
    val id: String,
    val title: String,
    val description: String = "",
    val icon: ImageVector? = null
)
