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
    /** Being checked, or being asked for right now. Shown with a spinner. */
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
    val isCheckingAdb: Boolean = true,
) {
    val isNotificationsInProgress: Boolean
        get() = itemStatus("Notification access") == ChecklistStatus.InProgress

    val isOverlayAccessInProgress: Boolean
        get() = itemStatus("Overlay access") == ChecklistStatus.InProgress

    /**
     * Whether the named item is still holding the gate closed.
     *
     * Overlay access is not blocking, so this always reports false for it. Exposed so the gate's
     * own rule can be asserted directly, rather than through isReady, which additionally waits on
     * a real process snapshot produced off the test scheduler.
     */
    fun isBlockingItemOutstanding(item: ChecklistItem): Boolean {
        if (item == ChecklistItem.OverlayAccess) return false
        val status = itemStatus(labelOf(item)) ?: return false
        return status != ChecklistStatus.Ready && status != ChecklistStatus.NotNeeded
    }

    private fun itemStatus(label: String): ChecklistStatus? =
        items.firstOrNull { it.label == label }?.status

    private fun labelOf(item: ChecklistItem): String = when (item) {
        ChecklistItem.AdbDebugging -> "USB debugging"
        ChecklistItem.Notifications -> "Notification access"
        ChecklistItem.OverlayAccess -> "Overlay access"
    }
}

/** Ordered so the checklist always renders the same rows, whatever has been checked yet. */
enum class ChecklistItem { AdbDebugging, Notifications, OverlayAccess }
