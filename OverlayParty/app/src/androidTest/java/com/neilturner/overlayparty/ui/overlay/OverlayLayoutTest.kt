package com.neilturner.overlayparty.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.neilturner.overlayparty.ui.theme.OverlayPartyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OverlayLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setLayout(
        topStart: OverlayContent? = null,
        topEnd: OverlayContent? = null,
        bottomStart: OverlayContent? = null,
        bottomEnd: OverlayContent? = null,
    ) {
        composeTestRule.setContent {
            OverlayPartyTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    OverlayLayout(
                        topStart = topStart,
                        topEnd = topEnd,
                        bottomStart = bottomStart,
                        bottomEnd = bottomEnd,
                    )
                }
            }
        }
    }

    private fun bounds(text: String) = composeTestRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    @Test
    fun topOverlaysDoNotOverlap() {
        setLayout(
            topStart = OverlayContent.TextOnly("top-start"),
            topEnd = OverlayContent.TextOnly("top-end"),
        )

        val start = bounds("top-start")
        val end = bounds("top-end")

        assertTrue(
            "start.right=${start.right} must be <= end.left=${end.left}",
            start.right <= end.left,
        )
    }

    @Test
    fun topAndBottomRowsDoNotOverlap() {
        setLayout(
            topStart = OverlayContent.TextOnly("top-start"),
            bottomStart = OverlayContent.TextOnly("bottom-start"),
        )

        val top = bounds("top-start")
        val bottom = bounds("bottom-start")

        assertTrue(
            "top.bottom=${top.bottom} must be <= bottom.top=${bottom.top}",
            top.bottom <= bottom.top,
        )
    }

    @Test
    fun verticalStackAppliesPadding() {
        setLayout(
            topStart =
                OverlayContent.VerticalStack(
                    items =
                        listOf(
                            OverlayContent.TextOnly("stack-a"),
                            OverlayContent.TextOnly("stack-b"),
                        ),
                    padding = 32f,
                ),
        )

        val first = bounds("stack-a")
        val minimumTop = with(composeTestRule.density) { 40.dp.toPx() }

        assertTrue(
            "stack-a.top=${first.top} must be >= $minimumTop",
            first.top >= minimumTop,
        )
    }
}
