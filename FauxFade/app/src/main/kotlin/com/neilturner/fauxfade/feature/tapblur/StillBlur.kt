package com.neilturner.fauxfade.feature.tapblur

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Blurs a tapped still before it is used to cover a handoff.
 *
 * A full-resolution Gaussian on a captured 4K frame would cost more than the tap itself, so the
 * blur runs on a small copy and is scaled back up. The upscale's bilinear filter does part of the
 * softening, and two separable box passes on the small copy do the rest. It is deliberately cheap:
 * the blur sits in the handoff's critical path, on top of the readback we are already waiting for.
 */
internal object StillBlur {
	private const val WORK_DIMENSION = 480
	private const val RADIUS = 8
	private const val PASSES = 2

	fun blur(source: Bitmap): Bitmap {
		val scale = min(1f, WORK_DIMENSION.toFloat() / max(source.width, source.height))
		val w = max(1, (source.width * scale).toInt())
		val h = max(1, (source.height * scale).toInt())
		var work: Bitmap = Bitmap.createScaledBitmap(source, w, h, true)
		repeat(PASSES) {
			work = boxBlur(work, horizontal = true)
			work = boxBlur(work, horizontal = false)
		}
		return Bitmap.createScaledBitmap(work, source.width, source.height, true)
	}

	/** One sliding-window box pass, horizontal or vertical. Alpha is ignored - a still is opaque. */
	private fun boxBlur(
		source: Bitmap,
		horizontal: Boolean,
	): Bitmap {
		val w = source.width
		val h = source.height
		val input = IntArray(w * h)
		source.getPixels(input, 0, w, 0, 0, w, h)
		val output = IntArray(w * h)

		val outer = if (horizontal) h else w
		val inner = if (horizontal) w else h
		for (a in 0 until outer) {
			for (b in 0 until inner) {
				var r = 0
				var g = 0
				var blue = 0
				var count = 0
				for (i in max(0, b - RADIUS)..min(inner - 1, b + RADIUS)) {
					val index = if (horizontal) a * w + i else i * w + a
					val pixel = input[index]
					r += (pixel shr 16) and 0xFF
					g += (pixel shr 8) and 0xFF
					blue += pixel and 0xFF
					count++
				}
				val index = if (horizontal) a * w + b else b * w + a
				output[index] =
					(0xFF000000.toInt()) or
					(r / count shl 16) or
					(g / count shl 8) or
					(blue / count)
			}
		}

		val result = Bitmap.createBitmap(w, h, source.config ?: Bitmap.Config.ARGB_8888)
		result.setPixels(output, 0, w, 0, 0, w, h)
		return result
	}
}
