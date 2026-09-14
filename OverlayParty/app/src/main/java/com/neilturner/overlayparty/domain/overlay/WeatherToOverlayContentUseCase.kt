package com.neilturner.overlayparty.domain.overlay

import com.neilturner.overlayparty.data.WeatherInfo
import com.neilturner.overlayparty.ui.overlay.OverlayAnimationType
import com.neilturner.overlayparty.ui.overlay.OverlayContent
import com.neilturner.overlayparty.ui.overlay.OverlayIcon
import com.neilturner.overlayparty.ui.overlay.OverlayItem

class WeatherToOverlayContentUseCase {
    operator fun invoke(
        weather: WeatherInfo,
        animationType: OverlayAnimationType = OverlayAnimationType.FADE,
    ): OverlayContent =
        OverlayContent.MultiItemContent(
            items =
                listOf(
                    OverlayItem.Text(weather.city),
                    OverlayItem.Icon(mapConditionToIcon(weather.condition)),
                    OverlayItem.Text(weather.temperature),
                ),
            animationType = animationType,
            padding = 4f,
        )

    private fun mapConditionToIcon(condition: String) = when (condition) {
        "Sunny" -> OverlayIcon.WbSunny
        "Cloudy" -> OverlayIcon.Cloud
        "Rainy" -> OverlayIcon.WaterDrop
        "Snowy" -> OverlayIcon.AcUnit
        else -> OverlayIcon.Cloud
    }
}