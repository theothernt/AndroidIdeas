package com.neilturner.visualmashup.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.visualmashup.R
import com.neilturner.visualmashup.ui.theme.VisualMashupTheme

/**
 * The app's entry screen. Shows the title and the main actions on a single row.
 *
 * The first button is focused on first composition so the D-pad has a clear starting point.
 */
@Composable
fun MenuScreen(
    onNavigateToCollage: () -> Unit,
    onNavigateToCollagePreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.menu_title),
            style = MaterialTheme.typography.displayMedium,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onNavigateToCollage,
                modifier = Modifier.focusRequester(focusRequester),
            ) {
                Text(text = stringResource(R.string.open_collage))
            }
            Button(onClick = onNavigateToCollagePreview) {
                Text(text = stringResource(R.string.collage_preview))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun MenuScreenPreview() {
    VisualMashupTheme {
        MenuScreen(
            onNavigateToCollage = {},
            onNavigateToCollagePreview = {},
        )
    }
}