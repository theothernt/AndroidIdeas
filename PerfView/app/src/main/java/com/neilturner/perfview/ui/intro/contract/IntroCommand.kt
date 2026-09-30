package com.neilturner.perfview.ui.intro.contract

sealed interface IntroCommand {
    /**
     * ADB is authorized and proven, so the dashboard can be shown.
     */
    data object NavigateToDashboard : IntroCommand

    data object ExitApp : IntroCommand
}