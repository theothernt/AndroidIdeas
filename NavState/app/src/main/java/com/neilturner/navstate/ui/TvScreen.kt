package com.neilturner.navstate.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

@Composable
fun TvScreenColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        item {
            Column(
                horizontalAlignment = horizontalAlignment,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                content = content,
            )
        }
    }
}

@Composable
fun FocusableText(text: String, modifier: Modifier = Modifier, fontSize: Int = 24) {
    Text(
        text = text,
        modifier = modifier.padding(16.dp),
        fontSize = fontSize.sp,
    )
}