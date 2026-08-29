package com.neilturner.navstate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neilturner.navstate.viewmodel.ScreenSixViewModel

@Composable
fun ScreenSix(
    onNavigateToScreenFive: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScreenSixViewModel = viewModel()
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Screen Six", modifier = Modifier.padding(16.dp))
        Text(text = "Counter: ${viewModel.counter}", modifier = Modifier.padding(16.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNavigateToScreenFive) {
            Text(text = "Back to Screen 5")
        }
    }
}