package com.neilturner.overlayparty.di

import com.neilturner.overlayparty.data.CountdownRepository
import com.neilturner.overlayparty.data.LocationRepository
import com.neilturner.overlayparty.data.MessageRepository
import com.neilturner.overlayparty.data.MusicRepository
import com.neilturner.overlayparty.data.TimeRepository
import com.neilturner.overlayparty.data.WeatherRepository
import com.neilturner.overlayparty.domain.overlay.BottomStartOverlayContentUseCase
import com.neilturner.overlayparty.domain.overlay.LocationMessageToOverlayContentUseCase
import com.neilturner.overlayparty.domain.overlay.MusicToOverlayContentUseCase
import com.neilturner.overlayparty.domain.overlay.OverlayVisibilityManager
import com.neilturner.overlayparty.domain.overlay.TimeToOverlayContentUseCase
import com.neilturner.overlayparty.domain.overlay.WeatherToOverlayContentUseCase
import com.neilturner.overlayparty.ui.main.MainViewModel
import com.neilturner.overlayparty.ui.main.ScreenTwoViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule =
    module {
        single { WeatherRepository() }
        single { TimeRepository() }
        single { MusicRepository() }
        single { LocationRepository() }
        single { MessageRepository() }
        single { CountdownRepository() }

        // Use cases
        single { WeatherToOverlayContentUseCase() }
        single { TimeToOverlayContentUseCase() }
        single { MusicToOverlayContentUseCase() }
        single { BottomStartOverlayContentUseCase(get()) }
        single { LocationMessageToOverlayContentUseCase() }
        single { OverlayVisibilityManager() }

        viewModel {
            MainViewModel(
                weatherRepository = get(),
                timeRepository = get(),
                musicRepository = get(),
                locationRepository = get(),
                messageRepository = get(),
                countdownRepository = get(),
                visibility = get(),
                weatherMapper = get(),
                timeMapper = get(),
                bottomStartMapper = get(),
                bottomEndMapper = get(),
            )
        }
        viewModel {
            ScreenTwoViewModel(
                weatherRepository = get(),
                timeRepository = get(),
                musicRepository = get(),
                locationRepository = get(),
                messageRepository = get(),
                countdownRepository = get(),
                visibility = get(),
                weatherMapper = get(),
                timeMapper = get(),
                bottomStartMapper = get(),
                bottomEndMapper = get(),
            )
        }
    }