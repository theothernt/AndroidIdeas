package com.neilturner.visualmashup.ui.effects

import kotlinx.coroutines.flow.MutableStateFlow

/** Process-wide transition setting, shared by the menu and the Effects screen. */
object TransitionSettings {
    val transition = MutableStateFlow(Transition.CROSSFADE)
}