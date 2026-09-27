package com.neilturner.videothumbnails.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.videothumbnails.data.VideoCategory
import com.neilturner.videothumbnails.ui.theme.RailSelectedLabel
import com.neilturner.videothumbnails.ui.theme.RailSelectedPill
import com.neilturner.videothumbnails.ui.theme.RailUnselectedLabel
import kotlinx.coroutines.launch

private const val ALL_CATEGORIES_ID = "all"
private const val RAIL_FADE_MILLIS = 200
private const val RAIL_FOCUSED_SCALE = 1.05f
private val RAIL_PILL_SHAPE = RoundedCornerShape(percent = 50)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoryRail(
    categories: List<VideoCategory>,
    selectedCategory: VideoCategory?,
    onSelectCategory: (VideoCategory?) -> Unit,
    onSelectedFocusReady: (() -> Unit) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val itemFocusRequesters =
        remember(categories) {
            buildMap {
                put(ALL_CATEGORIES_ID, FocusRequester())
                categories.forEach { category -> put(category.id, FocusRequester()) }
            }
        }
    val allFocusRequester = itemFocusRequesters.getValue(ALL_CATEGORIES_ID)
    val scope = rememberCoroutineScope()
    val selectedKey by rememberUpdatedState(selectedCategory?.id ?: ALL_CATEGORIES_ID)
    val readyCallback by rememberUpdatedState(onSelectedFocusReady)

    LaunchedEffect(Unit) {
        allFocusRequester.requestFocus()
    }

    LaunchedEffect(itemFocusRequesters) {
        readyCallback {
            scope.launch { itemFocusRequesters[selectedKey]?.requestFocus() }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = ALL_CATEGORIES_ID) {
            CategoryRailItem(
                displayName = "All",
                isSelected = selectedCategory == null,
                onFocused = { onSelectCategory(null) },
                onClick = { onSelectCategory(null) },
                modifier = Modifier.focusRequester(allFocusRequester),
            )
        }

        items(items = categories, key = { it.id }) { category ->
            CategoryRailItem(
                displayName = category.displayName,
                isSelected = selectedCategory == category,
                onFocused = { onSelectCategory(category) },
                onClick = { onSelectCategory(category) },
                modifier = Modifier.focusRequester(itemFocusRequesters.getValue(category.id)),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryRailItem(
    displayName: String,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) RailSelectedPill else Color.Transparent,
        animationSpec = tween(durationMillis = RAIL_FADE_MILLIS),
        label = "railPillColor",
    )

    val labelColor by animateColorAsState(
        targetValue = if (isSelected || isFocused) RailSelectedLabel else RailUnselectedLabel,
        animationSpec = tween(durationMillis = RAIL_FADE_MILLIS),
        label = "railLabelColor",
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused) RAIL_FOCUSED_SCALE else 1f,
        animationSpec = tween(durationMillis = RAIL_FADE_MILLIS),
        label = "railScale",
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.background(containerColor, RAIL_PILL_SHAPE)
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleMedium,
            color = labelColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
