package com.neilturner.overlayparty.data

import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

data class DateTimeInfo(
    val time: String,
    val date: String
)

class TimeRepository {
    fun getTimeStream(showSeconds: Boolean = true): Flow<DateTimeInfo> = flow {
        val timePattern = if (showSeconds) "HH:mm:ss" else "HH:mm"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timeFormatter = DateTimeFormatter.ofPattern(timePattern)
            val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
            while (true) {
                val now = LocalDateTime.now()
                emit(DateTimeInfo(
                    time = now.format(timeFormatter),
                    date = now.format(dateFormatter)
                ))
                delay(1000)
            }
        } else {
            val timeFormatter = SimpleDateFormat(timePattern, Locale.getDefault())
            val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            while (true) {
                val now = Date()
                emit(DateTimeInfo(
                    time = timeFormatter.format(now),
                    date = dateFormatter.format(now)
                ))
                delay(1000)
            }
        }
    }
}
