package com.neilturner.twopane.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppearanceSettings() {
    var themeSelection by remember { mutableStateOf("System") }
    var useAmoledBlack by remember { mutableStateOf(false) }
    var fontSize by remember { mutableFloatStateOf(16f) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Appearance", style = MaterialTheme.typography.headlineMedium)
        Text("Customize the look and feel of the application.", modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

        Text("Theme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        
        listOf("Light", "Dark", "System").forEach { theme ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = (themeSelection == theme),
                    onClick = { themeSelection = theme }
                )
                Text(
                    text = theme,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Display Options", style = MaterialTheme.typography.titleMedium)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Checkbox(
                checked = useAmoledBlack,
                onCheckedChange = { useAmoledBlack = it }
            )
            Text("Use Pure Black (AMOLED)", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
