package com.neilturner.overlayparty.ui.overlay

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Position of the icon relative to the text.
 */
enum class IconPosition {
    LEADING,
    TRAILING
}

/**
 * Items that can be used in a flexible multi-item overlay.
 */
sealed interface OverlayItem {
    data class Text(val text: String) : OverlayItem
    data class Icon(val icon: ImageVector) : OverlayItem
}

/**
 * Animation types for overlay updates.
 */
enum class OverlayAnimationType {
    CONTENT_RESIZING, // Standard size animation, no fade
    FADE_AND_REPLACE  // Crossfade between content updates
}

/**
 * Sealed interface representing different types of overlay content.
 * This allows any overlay type to be assigned to any corner position.
 */
sealed interface OverlayContent {
    val animationType: OverlayAnimationType

    /**
     * Simple text-only overlay content.
     */
    data class TextOnly(
        val text: String,
        override val animationType: OverlayAnimationType = OverlayAnimationType.CONTENT_RESIZING
    ) : OverlayContent

    /**
     * Overlay content with an icon and text.
     */
    data class IconWithText(
        val text: String, 
        val icon: ImageVector,
        val iconPosition: IconPosition = IconPosition.LEADING,
        override val animationType: OverlayAnimationType = OverlayAnimationType.CONTENT_RESIZING
    ) : OverlayContent

    /**
     * Flexible content that can contain multiple items (text, icons, etc.).
     */
    data class MultiItemContent(
        val items: List<OverlayItem>,
        override val animationType: OverlayAnimationType = OverlayAnimationType.CONTENT_RESIZING
    ) : OverlayContent

    /**
     * Vertical stack of independent overlay contents.
     */
    data class VerticalStack(
        val items: List<OverlayContent>,
        override val animationType: OverlayAnimationType = OverlayAnimationType.CONTENT_RESIZING
    ) : OverlayContent
}
