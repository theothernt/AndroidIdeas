package com.neilturner.overlayparty.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class DateTimeInfo(
    val time: String,
    val date: String,
)

class TimeRepository {
    fun getTimeStream(showSeconds: Boolean = true): Flow<DateTimeInfo> =
        flow {
            val timePattern = if (showSeconds) "HH:mm:ss" else "HH:mm"
            val timeFormatter = DateTimeFormatter.ofPattern(timePattern)
            val datingFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

            while (true) {
                val now = LocalDateTime.now()
                emit(
                    DateTimeInfo(
                        time = now.format(timeFormatter),
                        date = now.format(datingFormatter),
                    ),
                )
                delay(1000)
            }
        }
}