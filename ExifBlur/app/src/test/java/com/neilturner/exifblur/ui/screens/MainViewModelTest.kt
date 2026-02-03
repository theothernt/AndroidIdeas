package com.neilturner.exifblur.ui.screens

import com.neilturner.exifblur.data.ExifMetadata
import com.neilturner.exifblur.data.ImageRepository
import com.neilturner.exifblur.util.BitmapHelper
import com.neilturner.exifblur.util.LocationHelper
import com.neilturner.exifblur.util.RamMonitor
import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MainViewModelTest {

    private lateinit var viewModel: MainViewModel
    private val imageRepository = mockk<ImageRepository>()
    private val locationHelper = mockk<LocationHelper>()
    private val bitmapHelper = mockk<BitmapHelper>()
    private val ramMonitor = mockk<RamMonitor>()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        
        every { imageRepository.isExifEnabled() } returns true
        viewModel = MainViewModel(imageRepository, locationHelper, bitmapHelper, ramMonitor)
    }

    @Test
    fun `resolveLocationOrModel combines location and time without GPS`() = runBlocking {
        val exif = ExifMetadata(
            date = "2024:10:27 15:30:45",
            offset = "+02:00",
            latitude = 51.5074,
            longitude = -0.1278
        )
        coEvery { locationHelper.getAddressFromLocation(51.5074, -0.1278) } returns "London, UK"
        
        val result = viewModel.resolveLocationOrModel(exif)
        
        assertEquals("London, UK • 27 Oct 2024 15:30:45 (+02:00)", result)
    }

    @Test
    fun `resolveLocationOrModel handles null location with time`() = runBlocking {
        val exif = ExifMetadata(
            date = "2024:10:27 15:30:45",
            offset = "+02:00",
            latitude = 51.5074,
            longitude = -0.1278
        )
        coEvery { locationHelper.getAddressFromLocation(51.5074, -0.1278) } returns null
        
        val result = viewModel.resolveLocationOrModel(exif)
        
        assertEquals("27 Oct 2024 15:30:45 (+02:00)", result)
    }

    @Test
    fun `resolveLocationOrModel formats date with time and offset`() = runBlocking {
        val exif = ExifMetadata(
            date = "2024:10:27 15:30:45",
            offset = "+02:00"
        )
        
        val result = viewModel.resolveLocationOrModel(exif)
        
        assertEquals("27 Oct 2024 15:30:45 (+02:00)", result)
    }

    @Test
    fun `resolveLocationOrModel formats date with time and no offset`() = runBlocking {
        val exif = ExifMetadata(
            date = "2024:10:27 15:30:45",
            offset = null
        )
        
        val result = viewModel.resolveLocationOrModel(exif)
        
        assertEquals("27 Oct 2024 15:30:45", result)
    }

    @Test
    fun `resolveLocationOrModel returns raw date if invalid format`() = runBlocking {
        val exif = ExifMetadata(
            date = "invalid-date",
            offset = "+02:00"
        )
        
        val result = viewModel.resolveLocationOrModel(exif)
        
        assertEquals("invalid-date", result)
    }
}
