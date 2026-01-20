package com.neilturner.overlayparty.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Date
import java.util.concurrent.TimeUnit

class CountdownRepository {
    
    fun getCountdownStream(durationMinutes: Long): Flow<String> = flow {
        val targetTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes)
        
        while (true) {
            val currentTime = System.currentTimeMillis()
            val remainingMillis = targetTime - currentTime
            
            if (remainingMillis > 0) {
                emit(formatRemainingTime(remainingMillis))
                delay(1000)
            } else {
                // Countdown complete
                emit("Countdown complete!")
                delay(10_000) // Show for 10 seconds
                emit("")    // Then disappear
                break
            }
        }
    }

    private fun formatRemainingTime(millis: Long): String {
        val days = TimeUnit.MILLISECONDS.toDays(millis)
        val hours = TimeUnit.MILLISECONDS.toHours(millis) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
