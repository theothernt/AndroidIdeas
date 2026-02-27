package com.neilturner.twopane.ui.mainmenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neilturner.twopane.data.MainMenuItem
import com.neilturner.twopane.ui.theme.TwoPaneTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMainMenu(
    items: List<MainMenuItem>,
    selectedItemId: String?,
    modifier: Modifier = Modifier,
    onItemClick: (MainMenuItem) -> Unit,
) {
    val focusRequesters = remember(items) { items.associate { it.id to FocusRequester() } }

    LaunchedEffect(Unit) {
        val requesterToFocus = selectedItemId?.let { focusRequesters[it] } ?: focusRequesters.values.firstOrNull()
        requesterToFocus?.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowItems.forEach { item ->
                    TvMenuRow(
                        item = item,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequesters[item.id] ?: FocusRequester()),
                        onClick = { onItemClick(item) }
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvMenuRow(
    item: MainMenuItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.large),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .pointerInput(onClick) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (item.icon != null) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
fun TvMainMenuPreview() {
    TwoPaneTheme {
        TvMainMenu(
            items = listOf(
                MainMenuItem(
                    id = "media",
                    title = "Media",
                    subtitle = "Browse and manage media",
                    icon = Icons.Default.PermMedia
                ),
                MainMenuItem(
                    id = "settings",
                    title = "Settings",
                    subtitle = "App preferences",
                    icon = Icons.Default.Settings
                ),
                MainMenuItem(
                    id = "about",
                    title = "About",
                    subtitle = "Version and information",
                    icon = Icons.Default.Info
                )
            ),
            selectedItemId = "media",
            onItemClick = {}
        )
    }
}
