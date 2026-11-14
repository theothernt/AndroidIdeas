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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.musicfun.R
import com.neilturner.musicfun.data.MusicMetadata
import com.neilturner.musicfun.data.lyrics.LrcLine
import com.neilturner.musicfun.ui.background.AuroraBackground
import com.neilturner.musicfun.ui.background.rememberAuroraColors
import com.neilturner.musicfun.ui.theme.MusicFunTheme
import com.neilturner.musicfun.ui.viewmodel.LyricsUiState
import com.neilturner.musicfun.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun MusicScreen(modifier: Modifier = Modifier) {
	val viewModel: MusicViewModel = viewModel()
	val metadata by viewModel.metadata.collectAsStateWithLifecycle()
	val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
	val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()

	MusicScreenContent(
		metadata = metadata,
		lyrics = lyrics,
		positionMs = positionMs,
		modifier = modifier,
	)
}

@Composable
private fun MusicScreenContent(
	metadata: MusicMetadata?,
	lyrics: LyricsUiState,
	positionMs: Long,
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
	LaunchedEffect(metadata, lyrics) {
		android.util.Log.d("AppState", "music metadata=${metadata?.title} lyrics=$lyrics")
	}

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

		LyricsPanel(
			state = lyrics,
			positionMs = positionMs,
			modifier = Modifier
				.align(Alignment.CenterEnd)
				.padding(end = 64.dp)
				.size(width = 420.dp, height = 370.dp),
		)

		Box(
			modifier = Modifier
				.matchParentSize()
				.padding(horizontal = 24.dp, vertical = 14.dp),
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
				horizontalArrangement = Arrangement.spacedBy(9.6.dp),
				verticalAlignment = Alignment.Bottom,
			) {
				AlbumArt(
					bitmap = artwork,
					modifier = Modifier.size(52.8.dp),
				)

				Column(modifier = Modifier.padding(bottom = 2.dp)) {
					Text(
						text = displayed.title ?: stringResource(R.string.unknown_title),
						style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.8.sp, lineHeight = 35.2.sp),
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

@Composable
private fun LyricsPanel(
	state: LyricsUiState,
	positionMs: Long,
	modifier: Modifier = Modifier,
) {
	when (state) {
		is LyricsUiState.Synced -> ScrollingLyrics(
			lines = state.lines,
			positionMs = positionMs,
			modifier = modifier,
		)

		is LyricsUiState.Plain -> StaticLyrics(
			text = state.text,
			modifier = modifier,
		)

		// Nothing to show while loading, instrumental, or when LRCLIB has no lyrics.
		LyricsUiState.Loading,
		LyricsUiState.Instrumental,
		LyricsUiState.None -> Unit
	}
}

/** Karaoke view: the current line is highlighted and the column scrolls to keep it centred. */
@Composable
private fun ScrollingLyrics(
	lines: List<LrcLine>,
	positionMs: Long,
	modifier: Modifier = Modifier,
) {
	val lineHeight = 64.dp
	val lineHeightPx = with(LocalDensity.current) { lineHeight.toPx() }
	val viewportHeightPx = with(LocalDensity.current) { 370.dp.toPx() }
	val currentIndex = remember(lines, positionMs) {
		lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
	}
	val scrollState = rememberScrollState()

	LaunchedEffect(currentIndex) {
		val target = (currentIndex * lineHeightPx + lineHeightPx / 2 - viewportHeightPx / 2)
			.toInt()
			.coerceAtLeast(0)
		scrollState.animateScrollTo(target)
	}

	Column(
		modifier = modifier
			.verticalScroll(scrollState)
			.padding(vertical = 185.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		lines.forEachIndexed { index, line ->
			val current = index == currentIndex
			Text(
				text = line.text,
				style = if (current) {
					MaterialTheme.typography.headlineSmall
				} else {
					MaterialTheme.typography.titleMedium
				},
				color = if (current) Color.White else Color.White.copy(alpha = 0.35f),
				textAlign = TextAlign.Center,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier
					.fillMaxWidth()
					.height(lineHeight),
			)
		}
	}
}

/** Plain (unsynced) lyrics: no auto-scroll, just readable text. */
@Composable
private fun StaticLyrics(text: String, modifier: Modifier = Modifier) {
	Column(
		modifier = modifier
			.verticalScroll(rememberScrollState())
			.padding(vertical = 24.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		text.lines().forEach { line ->
			Text(
				text = line,
				style = MaterialTheme.typography.bodyLarge,
				color = Color.White.copy(alpha = 0.7f),
				textAlign = TextAlign.Center,
				modifier = Modifier
					.fillMaxWidth()
					.padding(vertical = 4.dp),
			)
		}
	}
}

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun MusicScreenPreview() {
	MusicFunTheme {
		MusicScreenContent(
			metadata = null,
			lyrics = LyricsUiState.None,
			positionMs = 0L,
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
			lyrics = LyricsUiState.Synced(
				lines = listOf(
					LrcLine(0L, "First line of the song"),
					LrcLine(5_000L, "Second line of the song"),
					LrcLine(10_000L, "Third line of the song"),
					LrcLine(15_000L, "Fourth line of the song"),
					LrcLine(20_000L, "Fifth line of the song"),
				)
			),
			positionMs = 6_000L,
			modifier = Modifier.fillMaxSize(),
		)
	}
}