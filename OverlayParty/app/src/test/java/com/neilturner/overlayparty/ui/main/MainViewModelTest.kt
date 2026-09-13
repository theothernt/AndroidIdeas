package com.neilturner.overlayparty.ui.main

import com.neilturner.overlayparty.data.CountdownRepository
import com.neilturner.overlayparty.data.LocationRepository
import com.neilturner.overlayparty.data.MessageRepository
import com.neilturner.overlayparty.data.MusicRepository
import com.neilturner.overlayparty.data.TimeRepository
import com.neilturner.overlayparty.data.WeatherRepository
import com.neilturner.overlayparty.ui.overlay.OverlayPosition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private fun createViewModel() =
        MainViewModel(
            weatherRepository = WeatherRepository(),
            timeRepository = TimeRepository(),
            musicRepository = MusicRepository(),
            locationRepository = LocationRepository(),
            messageRepository = MessageRepository(),
            countdownRepository = CountdownRepository(),
        )

    @Test
    fun allOverlaysVisibleByDefault() =
        runTest {
            val viewModel = createViewModel()
            val visibleOverlays = viewModel.visibleOverlays.first()

            assertEquals(4, visibleOverlays.size)
            assertTrue(visibleOverlays.contains(OverlayPosition.TOP_START))
            assertTrue(visibleOverlays.contains(OverlayPosition.TOP_END))
            assertTrue(visibleOverlays.contains(OverlayPosition.BOTTOM_START))
            assertTrue(visibleOverlays.contains(OverlayPosition.BOTTOM_END))
        }

    @Test
    fun toggleOverlayHidesOverlayWhenVisible() =
        runTest {
            val viewModel = createViewModel()

            viewModel.toggleOverlay(OverlayPosition.TOP_START)

            val visibleOverlays = viewModel.visibleOverlays.first()
            assertFalse(visibleOverlays.contains(OverlayPosition.TOP_START))
            assertEquals(3, visibleOverlays.size)
        }

    @Test
    fun setOverlayVisibilityControlsVisibility() =
        runTest {
            val viewModel = createViewModel()

            viewModel.setOverlayVisibility(OverlayPosition.BOTTOM_END, false)
            val visibleAfterHide = viewModel.visibleOverlays.first()
            assertFalse(visibleAfterHide.contains(OverlayPosition.BOTTOM_END))

            viewModel.setOverlayVisibility(OverlayPosition.BOTTOM_END, true)
            val visibleAfterShow = viewModel.visibleOverlays.first()
            assertTrue(visibleAfterShow.contains(OverlayPosition.BOTTOM_END))
        }
}
