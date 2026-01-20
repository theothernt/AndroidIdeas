package com.neilturner.overlayparty.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MessageRepository {
    private val countries = listOf(
        "France", "Germany", "Italy", "Spain", "Portugal",
        "Brazil", "Argentina", "Japan", "China", "India"
    )

    fun getMessageStream(): Flow<String> = flow {
        var index = 0
        while (true) {
            emit(countries[index])
            index = (index + 1) % countries.size
            delay(3_000)
        }
    }
}
