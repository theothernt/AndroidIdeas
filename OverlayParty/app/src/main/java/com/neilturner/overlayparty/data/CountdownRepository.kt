package com.neilturner.overlayparty.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.TimeUnit

class CountdownRepository(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun getCountdownStream(durationMinutes: Long): Flow<String> =
        flow {
            while (true) {
                val targetTime = clock() + TimeUnit.MINUTES.toMillis(durationMinutes)

                while (true) {
                    val remainingMillis = targetTime - clock()

                    if (remainingMillis <= 0) break

                    emit(formatRemainingTime(remainingMillis))
                    delay(1000)
                }

                emit("Countdown complete!")
                delay(10_000) // Show for 10 seconds before starting again
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
