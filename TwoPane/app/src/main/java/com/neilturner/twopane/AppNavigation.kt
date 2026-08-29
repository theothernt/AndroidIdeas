package com.neilturner.twopane

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppNavKey : NavKey {
    @Serializable
    data object Start : AppNavKey
    @Serializable
    data object MainMenu : AppNavKey
    @Serializable
    data object Media : AppNavKey
    @Serializable
    data object Settings : AppNavKey
    @Serializable
    data object NewMenu : AppNavKey
}
