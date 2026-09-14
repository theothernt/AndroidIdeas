package com.neilturner.overlayparty.domain.overlay

import androidx.compose.ui.unit.dp
import com.neilturner.overlayparty.data.DateTimeInfo
import com.neilturner.overlayparty.ui.overlay.OverlayAnimationType
import com.neilturner.overlayparty.ui.overlay.OverlayContent
import com.neilturner.overlayparty.ui.overlay.StackAlignment

class TimeToOverlayContentUseCase {
    operator fun invoke(
        dateTime: DateTimeInfo,
        animationType: OverlayAnimationType = OverlayAnimationType.FADE,
    ): OverlayContent =
        OverlayContent.VerticalStack(
            items =
                listOf(
                    OverlayContent.TextOnly(
                        dateTime.date,
                        padding = 4.dp,
                        animationType = animationType,
                    ),
                    OverlayContent.TextOnly(
                        dateTime.time,
                        scale = 2f,
                        padding = 4.dp,
                        animationType = animationType,
                    ),
                ),
            alignment = StackAlignment.END,
        )
}