package com.neilturner.visualmashup.ui.effects

import kotlinx.coroutines.flow.MutableStateFlow

/** Process-wide effect settings, shared by the menu and the Effects screen. */
object EffectSettings {
    val speed = MutableStateFlow(EffectSpeed.NORMAL)
    val effect = MutableStateFlow(ImageEffect.KEN_BURNS)
}