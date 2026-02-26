package com.neilturner.twopane.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Checkbox
import androidx.tv.material3.CheckboxDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.RadioButtonDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neilturner.twopane.ui.theme.TwoPaneTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    var checkboxChecked by remember { mutableStateOf(false) }
    var radioSelected by remember { mutableStateOf(false) }
    var isCheckboxFocused by remember { mutableStateOf(false) }
    var isRadioFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Surface(
            onClick = { checkboxChecked = !checkboxChecked },
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isCheckboxFocused = it.isFocused }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Checkbox(
                    checked = checkboxChecked,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = if (isCheckboxFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.primary,
                        uncheckedColor = if (isCheckboxFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
                        checkmarkColor = if (isCheckboxFocused) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surface
                    )
                )
                Text(
                    text = "Enable background play",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCheckboxFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Surface(
            onClick = { radioSelected = !radioSelected },
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isRadioFocused = it.isFocused }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RadioButton(
                    selected = radioSelected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = if (isRadioFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.primary,
                        unselectedColor = if (isRadioFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = "High quality audio",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isRadioFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        Text("Press Back to return", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(device = "id:tv_1080p")
@Composable
fun TvSettingsScreenPreview() {
    TwoPaneTheme {
        TvSettingsScreen(onBack = {})
    }
}
