package com.neilturner.twopane

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppNavKey : NavKey {
    @Serializable
    data object MainMenu : AppNavKey
    @Serializable
    data object Media : AppNavKey
}
