package com.neilturner.perfview.ui.intro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neilturner.perfview.ui.intro.contract.IntroContentState
import com.neilturner.perfview.ui.intro.contract.IntroViewState
import com.neilturner.perfview.ui.theme.PerfAmber
import com.neilturner.perfview.ui.theme.PerfInk
import com.neilturner.perfview.ui.theme.PerfMist
import com.neilturner.perfview.ui.theme.PerfSlate
import com.neilturner.perfview.ui.theme.PerfViewTheme

private val PanelBackground = Color(0xF2102A36)
private val PanelBorder = Color(0xFF3A5A6A)
private val PrimaryButton = Color(0xFF1A8A5C)
private val SecondaryText = Color(0xFF8AACB8)
private val PanelShape = RoundedCornerShape(18.dp)

@Composable
fun IntroScreen(
    uiState: IntroViewState,
    onRetry: () -> Unit,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PerfInk)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "PERF VIEW",
                color = PerfMist,
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )

            Panel(content = uiState.content)

            Actions(
                content = uiState.content,
                onRetry = onRetry,
                onExitApp = onExitApp,
            )
        }
    }
}

@Composable
private fun Panel(content: IntroContentState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBackground, PanelShape)
            .border(1.dp, PanelBorder, PanelShape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (content) {
            IntroContentState.Checking -> SpinnerBody(
                title = "Checking connection",
                detail = "Looking for an existing ADB connection",
            )

            IntroContentState.NeedsAuthorization -> NeedsAuthorizationBody()

            is IntroContentState.Authorizing -> SpinnerBody(
                title = content.message,
                detail = "Approve the debugging prompt on this device",
            )

            is IntroContentState.Verifying -> SpinnerBody(
                title = content.message,
                detail = "Waiting for the first process reading",
            )

            is IntroContentState.Failed -> FailedBody(message = content.message)
        }
    }
}

@Composable
private fun SpinnerBody(
    title: String,
    detail: String,
) {
    CircularProgressIndicator(
        modifier = Modifier.size(36.dp),
        color = PerfSlate,
        strokeWidth = 3.dp,
    )
    BodyText(title = title, color = PerfMist)
    BodyText(title = detail, color = PerfSlate)
}

@Composable
private fun NeedsAuthorizationBody() {
    BodyText(
        title = "Permission needed",
        color = PerfAmber,
    )
    BodyText(
        title = "Perf View was not granted ADB access. Enable wireless debugging or run " +
            "\"adb tcpip 5555\", then try again and allow the debugging prompt.",
        color = PerfSlate,
    )
}

@Composable
private fun FailedBody(message: String) {
    BodyText(title = "Connection failed", color = PerfAmber)
    BodyText(title = message, color = PerfSlate)
}

@Composable
private fun BodyText(
    title: String,
    color: Color,
) {
    Text(
        text = title,
        color = color,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center,
        lineHeight = 18.sp,
    )
}

@Composable
private fun Actions(
    content: IntroContentState,
    onRetry: () -> Unit,
    onExitApp: () -> Unit,
) {
    // Both terminal states are failed attempts of the same connect, so they share one action.
    if (content is IntroContentState.NeedsAuthorization || content is IntroContentState.Failed) {
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryButton,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = "Try again",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    TextButton(
        onClick = onExitApp,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = "Exit app",
            style = MaterialTheme.typography.bodyLarge,
            color = SecondaryText,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07131F)
@Composable
private fun IntroScreenPreview() {
    PerfViewTheme(dynamicColor = false) {
        IntroScreen(
            uiState = IntroViewState(),
            onRetry = {},
            onExitApp = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07131F)
@Composable
private fun IntroScreenNeedsAuthorizationPreview() {
    PerfViewTheme(dynamicColor = false) {
        IntroScreen(
            uiState = IntroViewState(content = IntroContentState.NeedsAuthorization),
            onRetry = {},
            onExitApp = {},
        )
    }
}