package com.neilturner.musicfun.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.musicfun.R
import com.neilturner.musicfun.data.MusicMetadata
import com.neilturner.musicfun.ui.background.AuroraBackground
import com.neilturner.musicfun.ui.background.rememberAuroraColors
import com.neilturner.musicfun.ui.theme.MusicFunTheme
import com.neilturner.musicfun.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun MusicScreen(modifier: Modifier = Modifier) {
	val viewModel: MusicViewModel = viewModel()
	val metadata by viewModel.metadata.collectAsStateWithLifecycle()

	MusicScreenContent(
		metadata = metadata,
		modifier = modifier,
	)
}

@Composable
private fun MusicScreenContent(
	metadata: MusicMetadata?,
	modifier: Modifier = Modifier,
) {
	// The displayed track only changes while the screen is black, so the swap is hidden.
	var displayedMetadata by remember { mutableStateOf(metadata) }
	var blackTarget by remember { mutableStateOf(0f) }
	val blackAlpha by animateFloatAsState(
		targetValue = blackTarget,
		animationSpec = tween(durationMillis = 160, easing = LinearEasing),
		label = "trackFade",
	)
	var isFirstMetadata by remember { mutableStateOf(true) }

	// Key on the track identity, not the metadata object: progress updates create new instances
	// (and possibly new bitmap instances) but must not restart the transition.
	val trackKey = metadata?.let { "${it.title}|${it.artist}|${it.album}" }
	LaunchedEffect(trackKey) {
		if (isFirstMetadata) {
			isFirstMetadata = false
			return@LaunchedEffect
		}
		blackTarget = 1f
		delay(160)
		displayedMetadata = metadata
		delay(20)
		blackTarget = 0f
	}

	// Prefer the bitmap from the media session; fall back to loading the URI.
	val uriBitmap = rememberUriBitmap(displayedMetadata?.artworkUri)
	val artwork = displayedMetadata?.artwork ?: uriBitmap
	val auroraColors = rememberAuroraColors(artwork)

	Box(modifier = modifier.fillMaxSize()) {
		AuroraBackground(
			colors = auroraColors,
			modifier = Modifier.matchParentSize(),
		)

		// Vignette to keep the edges calm and the centre focused.
		Box(
			modifier = Modifier
				.matchParentSize()
				.background(
					Brush.radialGradient(
						colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
					)
				)
		)

		// Scrim so the track text stays readable over the aurora.
		Box(
			modifier = Modifier
				.matchParentSize()
				.background(
					Brush.verticalGradient(
						0.5f to Color.Transparent,
						1f to Color.Black.copy(alpha = 0.92f),
					)
				)
		)

		Box(
			modifier = Modifier
				.matchParentSize()
				.padding(horizontal = 64.dp, vertical = 56.dp),
		) {
			val displayed = displayedMetadata
			if (displayed == null) {
				Text(
					text = stringResource(R.string.nothing_playing),
					modifier = Modifier.align(Alignment.BottomStart),
					style = MaterialTheme.typography.titleMedium,
					color = Color.White.copy(alpha = 0.6f),
				)
				return@Box
			}

			Row(
				modifier = Modifier.align(Alignment.BottomStart),
				horizontalArrangement = Arrangement.spacedBy(32.dp),
				verticalAlignment = Alignment.Bottom,
			) {
				AlbumArt(
					bitmap = artwork,
					modifier = Modifier.size(66.dp),
				)

				Column(modifier = Modifier.padding(bottom = 6.dp)) {
					Text(
						text = displayed.title ?: stringResource(R.string.unknown_title),
						style = MaterialTheme.typography.displaySmall,
						color = Color.White,
					)
					displayed.artist?.let { artist ->
						Text(
							text = artist,
							style = MaterialTheme.typography.titleMedium,
							color = Color.White.copy(alpha = 0.7f),
						)
					}
				}
			}
		}

		// Covers the swap: fade to black, change track, fade back in.
		Box(
			modifier = Modifier
				.matchParentSize()
				.background(Color.Black.copy(alpha = blackAlpha))
		)
	}
}

@Composable
private fun AlbumArt(
	bitmap: Bitmap?,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
			.clip(MaterialTheme.shapes.extraSmall)
			.background(Color(0xFF121212)),
		contentAlignment = Alignment.Center,
	) {
		val image = bitmap
		if (image != null) {
			Image(
				bitmap = image.asImageBitmap(),
				contentDescription = stringResource(R.string.album_art),
				modifier = Modifier.fillMaxSize(),
				contentScale = ContentScale.Crop,
			)
		} else {
			Text(
				text = stringResource(R.string.no_album_art),
				style = MaterialTheme.typography.labelSmall,
				color = Color.White.copy(alpha = 0.4f),
			)
		}
	}
}

@Composable
private fun rememberUriBitmap(uri: String?): Bitmap? {
	val context = LocalContext.current
	var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
	LaunchedEffect(uri) {
		bitmap = if (uri.isNullOrBlank()) {
			null
		} else {
			withContext(Dispatchers.IO) { decodeArtUri(context, uri) }
		}
	}
	return bitmap
}

private fun decodeArtUri(context: Context, uri: String, maxSize: Int = 512): Bitmap? {
	return try {
		val bytes = when {
			uri.startsWith("content://") || uri.startsWith("file://") ->
				context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
			else -> URL(uri).openStream().use { it.readBytes() }
		} ?: return null

		val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

		var sample = 1
		while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSize) {
			sample *= 2
		}
		val options = BitmapFactory.Options().apply { inSampleSize = sample }
		BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
	} catch (e: Exception) {
		Log.w("MusicFun", "Could not load art URI: $uri", e)
		null
	}
}

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun MusicScreenPreview() {
	MusicFunTheme {
		MusicScreenContent(
			metadata = null,
			modifier = Modifier.fillMaxSize(),
		)
	}
}

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun MusicScreenPlayingPreview() {
	MusicFunTheme {
		MusicScreenContent(
			metadata = MusicMetadata(
				packageName = "com.example.music",
				title = "Song Title",
				artist = "Artist Name",
				album = "Album Name",
			),
			modifier = Modifier.fillMaxSize(),
		)
	}
}