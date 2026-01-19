package com.neilturner.overlayparty.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class WeatherRepository {
    private val weatherLocations = listOf(
        "Dublin 10°C", "London 12°C", "Paris 14°C", "New York 20°C",
        "Tokyo 18°C", "Sydney 22°C", "Berlin 11°C", "Rome 16°C",
        "Madrid 17°C", "Toronto 15°C"
    )

    fun getWeatherStream(): Flow<String> = flow {
        var index = 0
        while (true) {
            emit(weatherLocations[index])
            index = (index + 1) % weatherLocations.size
            delay(11_000)
        }
    }
}
