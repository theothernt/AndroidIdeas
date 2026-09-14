package com.neilturner.overlayparty.domain.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.unit.dp
import com.neilturner.overlayparty.ui.overlay.IconPosition
import com.neilturner.overlayparty.ui.overlay.OverlayAnimationType
import com.neilturner.overlayparty.ui.overlay.OverlayContent

class MusicToOverlayContentUseCase {
    operator fun invoke(
        music: String,
        animationType: OverlayAnimationType = OverlayAnimationType.FADE,
    ): OverlayContent =
        OverlayContent.IconWithText(
            text = music,
            icon = Icons.Default.MusicNote,
            iconPosition = IconPosition.LEADING,
            animationType = animationType,
            padding = 4.dp,
        )
}