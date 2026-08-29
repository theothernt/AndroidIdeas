package com.neilturner.twopane.ui.start

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.neilturner.twopane.ui.theme.TwoPaneTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun StartScreen(
    onNavigateToOldMenu: () -> Unit,
    onNavigateToNewMenu: () -> Unit
) {
    val context = LocalContext.current
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    if (isTv) {
        TvStartScreen(
            modifier = Modifier.fillMaxSize(),
            onNavigateToOldMenu = onNavigateToOldMenu,
            onNavigateToNewMenu = onNavigateToNewMenu
        )
    } else {
        MobileStartScreen(
            modifier = Modifier.fillMaxSize(),
            onNavigateToOldMenu = onNavigateToOldMenu,
            onNavigateToNewMenu = onNavigateToNewMenu
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileStartScreen(
    modifier: Modifier = Modifier,
    onNavigateToOldMenu: () -> Unit,
    onNavigateToNewMenu: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose Menu") }
            )
        },
        modifier = modifier.safeDrawingPadding()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select a Menu",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = onNavigateToOldMenu,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Old Menu")
            }

            Button(
                onClick = onNavigateToNewMenu,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("New Menu")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvStartScreen(
    modifier: Modifier = Modifier,
    onNavigateToOldMenu: () -> Unit,
    onNavigateToNewMenu: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Select a Menu",
            style = MaterialTheme.typography.headlineMedium
        )

        androidx.tv.material3.Button(
            onClick = onNavigateToOldMenu,
            scale = ButtonDefaults.scale(focusedScale = 1.05f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Old Menu")
        }

        androidx.tv.material3.Button(
            onClick = onNavigateToNewMenu,
            scale = ButtonDefaults.scale(focusedScale = 1.05f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("New Menu")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MobileStartScreenPreview() {
    TwoPaneTheme {
        StartScreen(
            onNavigateToOldMenu = {},
            onNavigateToNewMenu = {}
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
fun TvStartScreenPreview() {
    TwoPaneTheme {
        StartScreen(
            onNavigateToOldMenu = {},
            onNavigateToNewMenu = {}
        )
    }
}
