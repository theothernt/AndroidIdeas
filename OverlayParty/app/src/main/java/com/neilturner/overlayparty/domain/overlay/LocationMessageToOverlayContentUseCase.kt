package com.neilturner.overlayparty.domain.overlay

import com.neilturner.overlayparty.ui.overlay.OverlayAnimationType
import com.neilturner.overlayparty.ui.overlay.OverlayContent
import com.neilturner.overlayparty.ui.overlay.StackAlignment

class LocationMessageToOverlayContentUseCase {
    operator fun invoke(
        location: String?,
        message: String?,
        animationType: OverlayAnimationType = OverlayAnimationType.RESIZE,
    ): OverlayContent? {
        val items =
            buildList {
                message?.let { add(OverlayContent.TextOnly(it, animationType = animationType)) }
                location?.let { add(OverlayContent.TextOnly(it, animationType = animationType)) }
            }
        return if (items.isEmpty()) null
        else OverlayContent.VerticalStack(items = items, alignment = StackAlignment.END)
    }
}