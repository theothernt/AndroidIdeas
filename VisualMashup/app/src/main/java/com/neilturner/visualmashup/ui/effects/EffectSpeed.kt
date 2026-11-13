package com.neilturner.visualmashup.ui.effects

/** How fast the effect plays. */
enum class EffectSpeed(
    val displayName: String,
    val effectDurationMillis: Int,
) {
    SLOW("Slow", 8_000),
    NORMAL("Normal", 5_000),
    FAST("Fast", 2_500),
}