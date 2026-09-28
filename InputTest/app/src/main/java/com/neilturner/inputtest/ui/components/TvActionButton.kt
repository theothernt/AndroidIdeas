package com.neilturner.inputtest.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * The standard action button for this TV app. Sized and centred for a 10-foot UI, and
 * leaning on the TV Material 3 defaults so the focus scale and glow do the work of
 * showing which button is selected.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvActionButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	Button(
		onClick = onClick,
		modifier = modifier
			.width(360.dp)
			.height(56.dp)
	) {
		Box(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center
		) {
			Text(
				text = text,
				style = MaterialTheme.typography.titleMedium,
				textAlign = TextAlign.Center
			)
		}
	}
}
