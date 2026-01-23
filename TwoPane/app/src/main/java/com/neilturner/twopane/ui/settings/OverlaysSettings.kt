package com.neilturner.twopane.ui.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

@Composable
fun OverlaysSettings() {
    var overlayMessage by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Overlays", style = MaterialTheme.typography.headlineMedium)
        Text("Configure on-screen overlays and widgets.", modifier = Modifier.padding(top = 8.dp))
        
        var text by remember { mutableStateOf("") }

        OutlinedTextField(
            value = text,
            onValueChange = { text =  it},
            modifier = Modifier
                .then(
                    if (isTv) {
                        Modifier
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    keyboardController?.hide()
                                }
                            }
                            .onPreviewKeyEvent { keyEvent -> 
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.key == Key.DirectionCenter) {
                                    keyboardController?.show()
                                    true
                                } else {
                                    false
                                }
                            }
                    } else {
                        Modifier
                    }
                )
        )
        
        Text(
            text = "This message will be displayed on the standby screen.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}