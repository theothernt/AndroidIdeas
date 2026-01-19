package com.neilturner.overlayparty.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TimeRepository {
    fun getTimeStream(): Flow<String> = flow {
        val formatter = DateTimeFormatter.ofPattern("MMM dd | HH:mm:ss")
        while (true) {
            emit(LocalDateTime.now().format(formatter))
            delay(1000)
        }
    }
}
