package com.neilturner.perfview.ui.intro.contract

sealed interface IntroIntent {
    /**
     * Fired when the intro becomes visible. Re-checks every item from live platform state, so
     * returning to the app reflects a permission the user may have just changed in Settings.
     */
    data object Load : IntroIntent

    /**
     * Act on whatever is outstanding, which is a permission prompt or a trip to Settings
     * depending on the item.
     */
    data object ActionClicked : IntroIntent

    /** The user answered the notification permission prompt. */
    data class NotificationPermissionResult(
        val granted: Boolean,
    ) : IntroIntent

    /** The user came back from the overlay permission screen in Settings. */
    data object OverlaySettingsResult : IntroIntent

    data object ExitApp : IntroIntent
}
