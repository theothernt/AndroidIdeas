package com.neilturner.overlayparty.domain.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.unit.dp
import com.neilturner.overlayparty.data.WeatherInfo
import com.neilturner.overlayparty.ui.overlay.OverlayAnimationType
import com.neilturner.overlayparty.ui.overlay.OverlayContent
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
            padding = 4.dp,
        )

    private fun mapConditionToIcon(condition: String) = when (condition) {
        "Sunny" -> Icons.Filled.WbSunny
        "Cloudy" -> Icons.Filled.Cloud
        "Rainy" -> Icons.Filled.WaterDrop
        "Snowy" -> Icons.Filled.AcUnit
        else -> Icons.Filled.Cloud
    }
}