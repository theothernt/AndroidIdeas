package com.neilturner.perfview.ui.intro.contract

sealed interface IntroIntent {
    /**
     * Fired once when the intro becomes visible. Starts the connect, which doubles as the
     * authorization request if the device has not trusted this key before.
     */
    data object Load : IntroIntent

    /**
     * Retries after a failed or declined attempt.
     */
    data object RetryClicked : IntroIntent

    data object ExitApp : IntroIntent
}