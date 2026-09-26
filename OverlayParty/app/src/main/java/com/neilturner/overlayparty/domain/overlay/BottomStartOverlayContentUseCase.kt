package com.neilturner.overlayparty.domain.overlay

import com.neilturner.overlayparty.ui.overlay.OverlayAnimationType
import com.neilturner.overlayparty.ui.overlay.OverlayContent
import com.neilturner.overlayparty.ui.overlay.StackAlignment

class BottomStartOverlayContentUseCase(
    private val musicToOverlayContent: MusicToOverlayContentUseCase,
) {
    operator fun invoke(
        music: String?,
        countdown: String?,
        countdownAnimationType: OverlayAnimationType = OverlayAnimationType.RESIZE,
        musicAnimationType: OverlayAnimationType = OverlayAnimationType.FADE,
    ): OverlayContent? {
        val items =
            buildList {
                countdown?.takeIf { it.isNotBlank() }?.let {
                    add(OverlayContent.TextOnly(it, padding = 4f, animationType = countdownAnimationType))
                }
                music?.takeIf { it.isNotBlank() }?.let {
                    add(musicToOverlayContent(it, musicAnimationType))
                }
            }
        return if (items.isEmpty()) {
            null
        } else {
            OverlayContent.VerticalStack(items = items, alignment = StackAlignment.START)
        }
    }
}
