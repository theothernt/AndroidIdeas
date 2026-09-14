package com.neilturner.overlayparty.domain.overlay

import com.neilturner.overlayparty.ui.overlay.IconPosition
import com.neilturner.overlayparty.ui.overlay.OverlayAnimationType
import com.neilturner.overlayparty.ui.overlay.OverlayContent
import com.neilturner.overlayparty.ui.overlay.OverlayIcon

class MusicToOverlayContentUseCase {
    operator fun invoke(
        music: String,
        animationType: OverlayAnimationType = OverlayAnimationType.FADE,
    ): OverlayContent =
        OverlayContent.IconWithText(
            text = music,
            icon = OverlayIcon.MusicNote,
            iconPosition = IconPosition.LEADING,
            animationType = animationType,
            padding = 4f,
        )
}