package com.neilturner.perfview.ui.intro.contract

import androidx.compose.runtime.Stable

/**
 * One line of the readiness checklist.
 */
@Stable
data class IntroChecklistItem(
    val label: String,
    val status: ChecklistStatus,
    val detail: String,
)

@Stable
enum class ChecklistStatus {
    /** Being checked or awaited, shown with a spinner. */
    InProgress,

    /** Satisfied. */
    Ready,

    /** Nothing to ask for on this platform. */
    NotNeeded,

    /** The user declined, or it could not be established. */
    NeedsAttention,
}

/**
 * The single action offered for whatever is outstanding.
 *
 * Kept as one action rather than one per row because only one thing can be actionable at a time:
 * the checklist settles in order, and the user works through them one by one.
 */
@Stable
sealed interface ChecklistAction {
    /** Ask for notifications, which the platform can do with a dialog. */
    data object RequestNotifications : ChecklistAction

    /** Send the user to Settings, the only route to the overlay permission. */
    data object OpenOverlaySettings : ChecklistAction

    /** Everything outstanding was declined or refused. */
    data object Retry : ChecklistAction
}

@Stable
data class IntroViewState(
    val items: List<IntroChecklistItem> = emptyList(),
    val isReady: Boolean = false,
    val isCheckingAdb: Boolean = true,
    val action: ChecklistAction? = null,
) {
    /**
     * A retry is only meaningful once a check has actually failed. While something is still in
     * progress there is nothing to retry, and offering it would just let the user start a second
     * attempt against the in-flight one.
     */
    val canAct: Boolean
        get() = items.any { it.status == ChecklistStatus.NeedsAttention }
}

/** Ordered so the checklist always renders the same rows, whatever has been checked yet. */
enum class ChecklistItem { AdbDebugging, Notifications, OverlayAccess }
