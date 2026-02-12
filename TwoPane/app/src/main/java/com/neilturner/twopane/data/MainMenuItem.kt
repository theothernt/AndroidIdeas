package com.neilturner.twopane.data

import androidx.compose.ui.graphics.vector.ImageVector

data class MainMenuItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val icon: ImageVector? = null
)
