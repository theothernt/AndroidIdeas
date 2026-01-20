package com.neilturner.overlayparty.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

data class WeatherInfo(
    val city: String,
    val temperature: String,
    val condition: String
)

class WeatherRepository {
    private val cities = listOf(
        "Dublin", "London", "Paris", "New York",
        "Tokyo", "Sydney", "Berlin", "Rome",
        "Madrid", "Toronto"
    )

    private val conditions = listOf("Sunny", "Cloudy", "Rainy", "Snowy")

    fun getWeatherStream(): Flow<WeatherInfo> = flow {
        var index = 0
        while (true) {
            val city = cities[index]
            val temp = "${Random.nextInt(0, 30)}°C"
            val condition = conditions.random()
            
            emit(WeatherInfo(city, temp, condition))
            
            index = (index + 1) % cities.size
            delay(5_000) // Faster updates for demo
        }
    }
}