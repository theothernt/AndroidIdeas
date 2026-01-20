package com.neilturner.overlayparty.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MusicRepository {
    private val nowPlayingSongs = listOf(
        "The Midnight\nDays of Thunder - Extended Mix",
        "Depeche Mode\nEverything Counts",
        "The Cure\nLovesong",
        "Tears for Fears\nEverybody Wants to Rule the World",
        "New Order\nBlue Monday",
        "Pet Shop Boys\nWest End Girls",
        "Erasure\nA Little Respect",
        "Simple Minds\nDon't You (Forget About Me)",
        "Duran Duran\nRio",
        "The Smiths\nThis Charming Man",
        "Joy Division\nLove Will Tear Us Apart"
    )

    fun getMusicStream(): Flow<String> = flow {
        var index = 0
        while (true) {
            emit(nowPlayingSongs[index])
            index = (index + 1) % nowPlayingSongs.size
            delay(5_000)
        }
    }
}
