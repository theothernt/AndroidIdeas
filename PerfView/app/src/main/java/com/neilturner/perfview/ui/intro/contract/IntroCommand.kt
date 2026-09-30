package com.neilturner.perfview.ui.intro.contract

sealed interface IntroCommand {
    /**
     * Every item is satisfied and the connection has been proven, so the dashboard can be shown.
     */
    data object NavigateToDashboard : IntroCommand

    /**
     * Ask the user for notification access.
     *
     * Raised here rather than at the overlay handoff: the handoff finishes the Activity, and a
     * permission dialog raised while that happens can take the process down with it.
     */
    data object RequestNotificationPermission : IntroCommand

    /**
     * Send the user to Settings to grant the overlay permission.
     *
     * There is no dialog equivalent for this permission, so this is the only route to it.
     */
    data object OpenOverlaySettings : IntroCommand

    data object ExitApp : IntroCommand
}
