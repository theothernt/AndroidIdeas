package com.neilturner.exifblur.slideshow

/** Choose how prefetched images are retained. Flip [mode] to switch. */
enum class CacheMode {
    /** Disk + RAM cache, two images ahead. */
    DISK,

    /** RAM only: current, next and previous, with no disk cache at all. */
    RAM_ONLY,
}

object CacheConfig {
    val mode: CacheMode = CacheMode.RAM_ONLY

    val prefetchWindow: Int
        get() = if (mode == CacheMode.RAM_ONLY) 1 else 2

    /** Keep the previous index tracked so it remains in the small RAM budget. */
    val keepPrevious: Boolean
        get() = mode == CacheMode.RAM_ONLY

    val diskCacheEnabled: Boolean
        get() = mode == CacheMode.DISK
}
