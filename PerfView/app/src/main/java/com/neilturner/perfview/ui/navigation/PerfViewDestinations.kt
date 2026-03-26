package com.neilturner.perfview.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Sealed interface for type-safe navigation destinations.
 * Each destination is marked with @Serializable for Navigation 3 type-safe navigation.
 */
sealed interface PerfViewDestinations : NavKey {
    @Serializable
    data object Dashboard : PerfViewDestinations
}
