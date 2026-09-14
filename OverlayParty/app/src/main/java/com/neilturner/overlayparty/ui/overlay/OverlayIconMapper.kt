package com.neilturner.overlayparty.ui.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps [OverlayIcon] enums to actual [ImageVector] instances for rendering.
 * This keeps Compose imports confined to the UI layer only.
 */
fun OverlayIcon.toImageVector(): ImageVector = when (this) {
    OverlayIcon.WbSunny -> Icons.Filled.WbSunny
    OverlayIcon.Cloud -> Icons.Filled.Cloud
    OverlayIcon.WaterDrop -> Icons.Filled.WaterDrop
    OverlayIcon.AcUnit -> Icons.Filled.AcUnit
    OverlayIcon.MusicNote -> Icons.Filled.MusicNote
}