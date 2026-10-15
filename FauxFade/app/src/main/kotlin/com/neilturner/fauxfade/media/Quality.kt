package com.neilturner.fauxfade.media

/**
 * The clip qualities on offer at startup, and the one a run starts on.
 *
 * Each maps to one of the five `url-*` keys the catalogue carries. All 139 assets in
 * tvos26.json have every one of them, so no entry here can come up short.
 *
 * The three numbers are all the same idea: how much headroom this resolution needs. A 4K clip
 * costs several times what a 1080p one does to buffer, to decode and to read a frame back, and
 * the handoff has a fixed amount of time to get a frame out of the incoming player and capture
 * a still from the outgoing one. Holding them to the 1080p figures makes half the swaps at 4K
 * time out and degrade to a cut.
 */
enum class Quality(
	val label: String,
	val bufferBytes: Int,
	val swapTimeoutMs: Long,
	val tapTimeoutMs: Long,
) {
	FHD_H264("1080p H.264", 24 * 1024 * 1024, 1_000L, 500L),
	FHD_SDR("1080p SDR", 24 * 1024 * 1024, 1_000L, 500L),

	// The 1080p figures, same as the SDR entry above: what these three numbers budget for is
	// resolution - buffer, decode and readback all scale with area. This stream is the 2K cut
	// of the 4K HDR master, so it is the same 1920x1080 to decode as 1080p SDR. Ten-bit decode
	// costs a little more than eight, but nowhere near enough to want the 4K figures.
	FHD_HDR("1080p HDR", 24 * 1024 * 1024, 1_000L, 500L),
	UHD_SDR("4K SDR", 64 * 1024 * 1024, 2_500L, 1_200L),
	UHD_HDR("4K HDR", 64 * 1024 * 1024, 2_500L, 1_200L),
	;

	/**
	 * The stream for this quality, or null if the asset does not carry it.
	 *
	 * Nullable rather than throwing: a missing variant is a property of the data, and the caller
	 * drops the asset and carries on with the rest of the ten.
	 */
	fun urlFor(asset: AerialAsset): String? =
		when (this) {
			FHD_H264 -> asset.url1080H264
			FHD_SDR -> asset.url1080SDR
			FHD_HDR -> asset.url1080HDR
			UHD_SDR -> asset.url4KSDR
			UHD_HDR -> asset.url4KHDR
		}
}
