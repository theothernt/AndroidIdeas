package com.neilturner.exifblur.util

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

class LocationHelper(private val context: Context) {

    /**
     * Access-ordered LRU cache of reverse-geocoded addresses so the same photo's
     * coordinates are not geocoded again on every slideshow cycle. Coordinates
     * are rounded to ~11m (4 decimal places) so nearby photos share entries.
     */
    private val addressCache = object : LinkedHashMap<String, String?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?): Boolean {
            return size > MAX_CACHE_ENTRIES
        }
    }

    suspend fun getAddressFromLocation(latitude: Double, longitude: Double): String? {
        return withContext(Dispatchers.IO) {
            val key = cacheKey(latitude, longitude)
            synchronized(addressCache) {
                if (addressCache.containsKey(key)) {
                    return@withContext addressCache[key]
                }
            }

            if (!Geocoder.isPresent()) {
                cacheAddress(key, null)
                return@withContext null
            }

            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCoroutine { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                            if (addresses.isNotEmpty()) {
                                val address = addresses[0]
                                val city = address.locality ?: address.subAdminArea ?: address.adminArea
                                val country = address.countryName
                                val name = when {
                                    city != null && country != null -> "$city, $country"
                                    city != null -> city
                                    else -> country
                                }
                                cont.resume(name)
                            } else {
                                cont.resume(null)
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val city = address.locality ?: address.subAdminArea ?: address.adminArea
                        val country = address.countryName
                        when {
                            city != null && country != null -> "$city, $country"
                            city != null -> city
                            else -> country
                        }
                    } else {
                        null
                    }
                }
                // Only successful lookups are cached so transient failures
                // (offline geocoder) are retried on the next image
                address?.let { cacheAddress(key, it) }
                address
            } catch (e: Exception) {
                Log.e("LocationHelper", "Reverse geocoding failed for $latitude,$longitude", e)
                null
            }
        }
    }

    private fun cacheAddress(key: String, value: String?) {
        synchronized(addressCache) {
            addressCache[key] = value
        }
    }

    private fun cacheKey(latitude: Double, longitude: Double): String {
        return "${(latitude * COORDINATE_PRECISION).roundToInt()}/${(longitude * COORDINATE_PRECISION).roundToInt()}"
    }

    companion object {
        private const val MAX_CACHE_ENTRIES = 100

        // 4 decimal places ≈ 11 metres of resolution
        private const val COORDINATE_PRECISION = 10_000.0
    }
}
