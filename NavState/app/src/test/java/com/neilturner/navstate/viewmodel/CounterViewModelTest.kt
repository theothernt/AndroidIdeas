package com.neilturner.navstate.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class CounterViewModelTest {

    @Test
    fun `counter starts at zero`() {
        assertEquals(0, CounterViewModel().counter.value)
    }

    @Test
    fun `increment adds one`() {
        val viewModel = CounterViewModel()

        viewModel.increment()
        viewModel.increment()

        assertEquals(2, viewModel.counter.value)
    }

    @Test
    fun `instances do not share state`() {
        val first = CounterViewModel()
        val second = CounterViewModel()

        first.increment()

        assertEquals(1, first.counter.value)
        assertEquals(0, second.counter.value)
    }
}
