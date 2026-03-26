package com.neilturner.perfview.ui.navigation

sealed interface PerfViewDestinations {
    data object Dashboard : PerfViewDestinations
}
