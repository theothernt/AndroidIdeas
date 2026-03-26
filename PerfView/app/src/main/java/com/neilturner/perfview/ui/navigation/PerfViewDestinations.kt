package com.neilturner.perfview.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Sealed interface for type-safe navigation destinations.
 * Each destination is marked with @Serializable for Navigation 2.8+ type-safe navigation.
 */
sealed interface PerfViewDestinations {
    @Serializable
    data object Dashboard : PerfViewDestinations
}
