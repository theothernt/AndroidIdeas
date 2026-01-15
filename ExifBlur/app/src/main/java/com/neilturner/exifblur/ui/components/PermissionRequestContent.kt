package com.neilturner.exifblur.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PermissionRequestContent(
    onRequestPermission: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onRequestPermission,
        modifier = modifier
            .focusRequester(focusRequester)
    ) {
        Text(text = "Ask for permission to access images")
    }
}
