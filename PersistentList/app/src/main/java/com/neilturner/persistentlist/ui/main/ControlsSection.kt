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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
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
    onClearDb: () -> Unit,
    onStartHighlighting: () -> Unit,
    onStopHighlighting: () -> Unit,
    isScanning: Boolean,
    isHighlighting: Boolean,
    sambaDuration: Long?,
    dbDuration: Long?,
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
	    TvButton(
		    text = "Start",
		    icon = Icons.Default.PlayArrow,
		    onClick = onStartHighlighting,
		    enabled = !isScanning && !isHighlighting,
		    modifier = Modifier.focusRequester(focusRequester)
	    )

	    Spacer(modifier = Modifier.height(12.dp))

	    // Stop button
	    TvButton(
		    text = "Stop",
		    icon = Icons.Default.Close,
		    onClick = onStopHighlighting,
		    enabled = isScanning || isHighlighting
	    )

	    Spacer(modifier = Modifier.height(24.dp))
        // Reload image list button
        TvButton(
            text = "Reload image list",
            icon = Icons.Default.Refresh,
            onClick = onLoadFromSamba,
            modifier = Modifier.focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(12.dp))
        
        TvButton(
            text = "Clear list",
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
        } else {
            if (sambaDuration != null) {
                Text(
                    text = "Samba scan: ${sambaDuration}ms",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            if (dbDuration != null) {
                Text(
                    text = "DB write/load: ${dbDuration}ms",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
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
