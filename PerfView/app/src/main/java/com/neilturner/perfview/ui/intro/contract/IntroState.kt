package com.neilturner.perfview.ui.intro.contract

import androidx.compose.runtime.Stable

@Stable
data class IntroViewState(
    val content: IntroContentState = IntroContentState.Checking,
)

/**
 * Lifecycle of the intro gate, which runs before the dashboard and decides whether ADB
 * authorization has to be requested before the process list can be shown.
 */
@Stable
sealed interface IntroContentState {
    /**
     * Connecting. This covers both looking for an existing connection and raising the
     * system debugging dialog, because a fresh key cannot be asked about without
     * triggering that dialog.
     */
    data object Checking : IntroContentState

    /**
     * The connect did not produce a usable connection. The user may have declined the
     * debugging dialog, or wireless debugging may not be enabled.
     */
    data object NeedsAuthorization : IntroContentState

    /**
     * The connect is still pending past the trusted-key grace period, which means the system
     * "Allow USB debugging?" dialog is up and waiting on the user.
     */
    data class Authorizing(
        val message: String,
    ) : IntroContentState

    /**
     * Authorized and connected. The connection is being proven against real process data
     * before the dashboard is shown, so it never renders a permanently broken list.
     */
    data class Verifying(
        val message: String,
    ) : IntroContentState

    /**
     * The attempt failed and cannot be retried automatically.
     */
    data class Failed(
        val message: String,
    ) : IntroContentState
}