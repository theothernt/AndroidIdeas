package com.neilturner.perfview.ui.intro

import com.neilturner.perfview.ui.intro.contract.IntroContentState
import com.neilturner.perfview.ui.intro.contract.IntroViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntroViewStateTest {

    @Test
    fun `default view state is checking`() {
        assertEquals(IntroContentState.Checking, IntroViewState().content)
    }

    @Test
    fun `verifying state carries its message`() {
        val content = IntroContentState.Verifying(message = "Checking connection")

        assertEquals("Checking connection", content.message)
    }

    @Test
    fun `failed state carries its message`() {
        val content = IntroContentState.Failed(message = "No process data arrived.")

        assertEquals("No process data arrived.", content.message)
    }

    @Test
    fun `needs authorization has no message`() {
        assertNull(IntroViewState(content = IntroContentState.NeedsAuthorization).content.messageOrNull())
    }
}

private fun IntroContentState.messageOrNull(): String? = when (this) {
    is IntroContentState.Verifying -> message
    is IntroContentState.Failed -> message
    IntroContentState.Checking,
    IntroContentState.NeedsAuthorization,
    -> null
}