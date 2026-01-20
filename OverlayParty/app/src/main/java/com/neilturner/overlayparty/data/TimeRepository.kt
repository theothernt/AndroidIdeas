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

class TimeRepository {
    fun getTimeStream(): Flow<String> = flow {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val formatter = DateTimeFormatter.ofPattern("MMM dd | HH:mm:ss")
            while (true) {
                emit(LocalDateTime.now().format(formatter))
                delay(1000)
            }
        } else {
            val formatter = SimpleDateFormat("MMM dd | HH:mm:ss", Locale.getDefault())
            while (true) {
                emit(formatter.format(Date()))
                delay(1000)
            }
        }
    }
}
