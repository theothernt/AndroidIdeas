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
    /**
     * Not reached yet. The checks run one at a time, so anything still queued must not look
     * like it is in progress.
     */
    Pending,

    /** Being checked or asked for right now. The only status that shows a spinner. */
    InProgress,

    /** Satisfied. */
    Ready,

    /** Nothing to ask for on this platform. */
    NotNeeded,

    /** Asked for and not granted, so it cannot be asked again automatically. */
    NeedsAttention,
}

@Stable
data class IntroViewState(
    val items: List<IntroChecklistItem> = emptyList(),
    val isReady: Boolean = false,
) {
    val isNotificationsInProgress: Boolean
        get() = itemStatus("Notification access") == ChecklistStatus.InProgress

    val isOverlayAccessInProgress: Boolean
        get() = itemStatus("Overlay access") == ChecklistStatus.InProgress

    private fun itemStatus(label: String): ChecklistStatus? =
        items.firstOrNull { it.label == label }?.status
}

/** Ordered so the checklist always renders the same rows, whatever has been checked yet. */
enum class ChecklistItem { AdbDebugging, Notifications, OverlayAccess }
