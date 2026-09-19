package com.neilturner.overlayparty.ui.main

import com.neilturner.overlayparty.data.CountdownRepository
import com.neilturner.overlayparty.data.LocationRepository
import com.neilturner.overlayparty.data.MessageRepository
import com.neilturner.overlayparty.data.MusicRepository
import com.neilturner.overlayparty.data.TimeRepository
import com.neilturner.overlayparty.data.WeatherRepository
import com.neilturner.overlayparty.ui.overlay.OverlayContent
import com.neilturner.overlayparty.ui.overlay.OverlayPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenTwoViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var testClock: Long = 0L

    private fun advanceVirtualTime(testScope: TestScope, millis: Long) {
        testClock += millis
        testScope.advanceTimeBy(millis)
    }

    private fun createViewModel(
        scope: CoroutineScope,
        visibleDurationMs: Long = ScreenTwoViewModel.VISIBLE_DURATION_MS,
        fadeOutDurationMs: Long = ScreenTwoViewModel.FADE_OUT_DURATION_MS,
        fadeInDurationMs: Long = ScreenTwoViewModel.FADE_IN_DURATION_MS,
    ) = run {
        testClock = 0L
        ScreenTwoViewModel(
            weatherRepository = WeatherRepository(),
            timeRepository = TimeRepository(),
            musicRepository = MusicRepository(),
            locationRepository = LocationRepository(),
            messageRepository = MessageRepository(),
            countdownRepository = CountdownRepository(clock = { testClock }),
            visibleDurationMs = visibleDurationMs,
            fadeOutDurationMs = fadeOutDurationMs,
            fadeInDurationMs = fadeInDurationMs,
            scope = scope,
        )
    }

    @Test
    fun allOverlaysVisibleByDefault() =
        runTest {
            val viewModel = createViewModel(this.backgroundScope)
            val visibleOverlays = viewModel.visibleOverlays.first()

            assertEquals(4, visibleOverlays.size)
            assertTrue(visibleOverlays.contains(OverlayPosition.TOP_START))
            assertTrue(visibleOverlays.contains(OverlayPosition.TOP_END))
            assertTrue(visibleOverlays.contains(OverlayPosition.BOTTOM_START))
            assertTrue(visibleOverlays.contains(OverlayPosition.BOTTOM_END))
        }

    @Test
    fun toggleOverlayHidesAndShowsOverlay() =
        runTest {
            val viewModel = createViewModel(this.backgroundScope)
            advanceVirtualTime(this, 200)
            runCurrent()

            viewModel.toggleOverlay(OverlayPosition.TOP_START)
            assertFalse(viewModel.visibleOverlays.value.contains(OverlayPosition.TOP_START))
            assertNull(viewModel.topStartOverlay.value)

            viewModel.toggleOverlay(OverlayPosition.TOP_START)
            assertTrue(viewModel.visibleOverlays.value.contains(OverlayPosition.TOP_START))
            assertNotNull(viewModel.topStartOverlay.value)
        }

    @Test
    fun setOverlayVisibilityControlsVisibility() =
        runTest {
            val viewModel = createViewModel(this.backgroundScope)
            advanceVirtualTime(this, 200)
            runCurrent()

            viewModel.setOverlayVisibility(OverlayPosition.BOTTOM_END, false)
            assertFalse(viewModel.visibleOverlays.value.contains(OverlayPosition.BOTTOM_END))
            assertNull(viewModel.bottomEndOverlay.value)

            viewModel.setOverlayVisibility(OverlayPosition.BOTTOM_END, true)
            assertTrue(viewModel.visibleOverlays.value.contains(OverlayPosition.BOTTOM_END))
            assertNotNull(viewModel.bottomEndOverlay.value)
        }

    @Test
    fun coordinatedFadeCycleFadesOutFlushesAndFadesIn() =
        runTest {
            val viewModel =
                createViewModel(
                    scope = this.backgroundScope,
                    visibleDurationMs = 5_000L,
                    fadeOutDurationMs = 500L,
                    fadeInDurationMs = 500L,
                )

            // Initially visible
            assertTrue(viewModel.isOverlaysVisible.value)
            assertEquals(1f, viewModel.groupAlpha.value, 0.001f)

            // Let initial collection run
            advanceVirtualTime(this, 150)
            runCurrent()

            // Initial content is populated
            val topStartInitial = viewModel.topStartOverlay.value
            assertTrue(topStartInitial is OverlayContent.MultiItemContent)

            // Advance through visible interval (5000ms)
            advanceVirtualTime(this, 5000)
            runCurrent()

            // Overlays fade out
            assertFalse(viewModel.isOverlaysVisible.value)
            assertEquals(0f, viewModel.groupAlpha.value, 0.001f)

            // Advance through fade out (500ms) + settle time (50ms)
            advanceVirtualTime(this, 550)
            runCurrent()

            // Overlays fade back in
            assertTrue(viewModel.isOverlaysVisible.value)
            assertEquals(1f, viewModel.groupAlpha.value, 0.001f)
        }
}