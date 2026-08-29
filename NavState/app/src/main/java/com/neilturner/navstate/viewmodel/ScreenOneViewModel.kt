package com.neilturner.navstate.viewmodel

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.ViewModel

class ScreenOneViewModel : ViewModel() {
    private val _counter = mutableIntStateOf(0)
    var counter: Int
        get() = _counter.intValue
        set(value) { _counter.intValue = value }

    fun increment() {
        _counter.intValue++
    }
}