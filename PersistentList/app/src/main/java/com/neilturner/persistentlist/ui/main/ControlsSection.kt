package com.neilturner.persistentlist.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ControlsSection(
    onLoadFromSamba: () -> Unit,
    onLoadFromDb: () -> Unit,
    onClearDb: () -> Unit,
    isScanning: Boolean,
    scanDuration: Long?,
    scanSource: String?,
    fileCount: Int,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        // Load from Samba button
        TvButton(
            text = "Load from Samba",
            icon = Icons.Default.Refresh,
            onClick = onLoadFromSamba,
            modifier = Modifier.focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(12.dp))
        
        // Load from DB button
        TvButton(
            text = "Load from DB",
            icon = Icons.Default.Refresh,
            onClick = onLoadFromDb
        )

        Spacer(modifier = Modifier.height(12.dp))

        TvButton(
            text = "Clear DB",
            icon = Icons.Default.Delete,
            onClick = onClearDb
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Scan Results
        if (isScanning) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scanning...",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        } else if (scanDuration != null) {
            Text(
                text = "Last scan: $scanDuration ms",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            if (scanSource != null) {
                Text(
                    text = "Source: $scanSource",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // File Count
        Text(
            text = "Files found: $fileCount",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
