package com.neilturner.musicfun.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.musicfun.R
import com.neilturner.musicfun.data.MusicMetadata
import com.neilturner.musicfun.data.lyrics.LrcLine
import com.neilturner.musicfun.data.trackKey
import com.neilturner.musicfun.ui.background.AuroraBackground
import com.neilturner.musicfun.ui.background.rememberAuroraColors
import com.neilturner.musicfun.ui.theme.MusicFunTheme
import com.neilturner.musicfun.ui.viewmodel.LyricsUi
import com.neilturner.musicfun.ui.viewmodel.LyricsUiState
import com.neilturner.musicfun.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.roundToInt

private const val FadeMs = 240

@Composable
fun MusicScreen(
	modifier: Modifier = Modifier,
	onRequestNotificationAccess: () -> Unit = {},
) {
	val viewModel: MusicViewModel = viewModel()
	val metadata by viewModel.nowPlaying.collectAsStateWithLifecycle()
	val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
	val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
	val hasNotificationAccess by viewModel.hasNotificationAccess.collectAsStateWithLifecycle()

	// Access can only be granted in system settings, so re-read it when we come back.
	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
		viewModel.refreshNotificationAccess()
	}

	MusicScreenContent(
		metadata = metadata,
		lyrics = lyrics,
		positionMs = positionMs,
		showPermissionRequest = hasNotificationAccess == false,
		onRequestNotificationAccess = onRequestNotificationAccess,
		modifier = modifier,
	)
}

@Composable
private fun MusicScreenContent(
	metadata: MusicMetadata?,
	lyrics: LyricsUi?,
	positionMs: Long,
	showPermissionRequest: Boolean = false,
	onRequestNotificationAccess: () -> Unit = {},
	modifier: Modifier = Modifier,
) {
	val currentArtwork = metadata?.artwork ?: rememberUriBitmap(metadata?.artworkUri)

	// Keep the last cover while the info fades out, so stopping doesn't snap the aurora back to
	// the brand palette mid-fade.
	var retainedArtwork by remember { mutableStateOf<Bitmap?>(null) }
	LaunchedEffect(currentArtwork) {
		if (currentArtwork != null) retainedArtwork = currentArtwork
	}
	val auroraColors = rememberAuroraColors(if (metadata != null) currentArtwork else retainedArtwork)

	Box(modifier = modifier.fillMaxSize()) {
		AuroraBackground(
			colors = auroraColors,
			modifier = Modifier.matchParentSize(),
		)

		// Vignette and scrim, pre-rendered into a single bitmap: as live Compose gradients they cost
		// two full-screen shader passes per frame, which on this TV is what held the screen at 30 fps.
		ScrimOverlay(modifier = Modifier.matchParentSize())

		// One fade for the whole now-playing layer: a track change fades the old row and lyrics
		// out and the new ones in, and null (paused or stopped) fades the layer away entirely.
		Crossfade(
			targetState = metadata,
			modifier = Modifier.matchParentSize(),
			animationSpec = tween(durationMillis = FadeMs, easing = LinearEasing),
			label = "nowPlaying",
		) { displayed ->
			if (displayed == null) {
				// Without notification access there is nothing to report; the request button below
				// takes this corner instead.
				if (!showPermissionRequest) {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.padding(horizontal = 24.dp, vertical = 14.dp),
						contentAlignment = Alignment.BottomStart,
					) {
						Text(
							text = stringResource(R.string.nothing_playing),
							style = MaterialTheme.typography.titleMedium,
							color = Color.White.copy(alpha = 0.6f),
						)
					}
				}
			} else {
				NowPlayingLayer(
					metadata = displayed,
					lyrics = lyrics?.takeIf { it.trackKey == displayed.trackKey }?.state
						?: LyricsUiState.None,
					positionMs = positionMs,
				)
			}
		}

		if (showPermissionRequest) {
			NotificationAccessButton(
				onClick = onRequestNotificationAccess,
				modifier = Modifier
					.align(Alignment.BottomStart)
					.padding(horizontal = 24.dp, vertical = 14.dp),
			)
		}
	}
}

/**
 * Bottom-left entry point for the notification-listener permission, which can only be granted in
 * system settings.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NotificationAccessButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
	val focusRequester = remember { FocusRequester() }
	LaunchedEffect(Unit) { focusRequester.requestFocus() }

	Button(
		onClick = onClick,
		modifier = modifier.focusRequester(focusRequester),
	) {
		Text(text = stringResource(R.string.request_notification_permission))
	}
}

/**
 * Vignette and bottom scrim baked into one bitmap. As live Compose gradients they cost two
 * full-screen shader passes every frame; as a single cached texture they cost one.
 */
@Composable
private fun ScrimOverlay(modifier: Modifier = Modifier) {
	BoxWithConstraints(modifier = modifier) {
		val widthPx = constraints.maxWidth
		val heightPx = constraints.maxHeight
		val scrim by produceState<ImageBitmap?>(initialValue = null, widthPx, heightPx) {
			value = if (widthPx <= 0 || heightPx <= 0) {
				null
			} else {
				withContext(Dispatchers.Default) { renderScrim(widthPx, heightPx) }
			}
		}

		scrim?.let { bitmap ->
			Image(
				bitmap = bitmap,
				contentDescription = null,
				contentScale = ContentScale.FillBounds,
				modifier = Modifier.fillMaxSize(),
			)
		}
	}
}

private fun renderScrim(widthPx: Int, heightPx: Int): ImageBitmap {
	val scale = 0.5f
	val width = (widthPx * scale).roundToInt().coerceAtLeast(1)
	val height = (heightPx * scale).roundToInt().coerceAtLeast(1)
	val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
	val canvas = android.graphics.Canvas(bitmap)

	val vignette = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		shader = RadialGradient(
			width / 2f,
			height / 2f,
			minOf(width, height) / 2f,
			intArrayOf(Color.Transparent.toArgb(), Color.Black.copy(alpha = 0.6f).toArgb()),
			null,
			Shader.TileMode.CLAMP,
		)
	}
	canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignette)

	val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		shader = LinearGradient(
			0f,
			height * 0.5f,
			0f,
			height.toFloat(),
			intArrayOf(Color.Transparent.toArgb(), Color.Black.copy(alpha = 0.92f).toArgb()),
			null,
			Shader.TileMode.CLAMP,
		)
	}
	canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)

	return bitmap.asImageBitmap()
}

@Composable
private fun NowPlayingLayer(
	metadata: MusicMetadata,
	lyrics: LyricsUiState,
	positionMs: Long,
) {
	val uriBitmap = rememberUriBitmap(metadata.artworkUri)
	val artwork = metadata.artwork ?: uriBitmap

	val progress = metadata.durationMs
		?.takeIf { it > 0 }
		?.let { duration -> (positionMs.toFloat() / duration).coerceIn(0f, 1f) }
		?: 0f

	Box(modifier = Modifier.fillMaxSize()) {
		// Lyrics usually land a moment after the track, so they fade in on their own. Another
		// Crossfade keeps the old lines on screen while they fade out.
		Crossfade(
			targetState = if (lyrics is LyricsUiState.Synced || lyrics is LyricsUiState.Plain) {
				lyrics
			} else {
				null
			},
			modifier = Modifier
				.align(Alignment.CenterEnd)
				.padding(end = 64.dp)
				.size(width = 420.dp, height = 370.dp),
			animationSpec = tween(durationMillis = FadeMs, easing = LinearEasing),
			label = "lyrics",
		) { panelLyrics ->
			if (panelLyrics != null) {
				LyricsPanel(
					state = panelLyrics,
					positionMs = positionMs,
					modifier = Modifier.fillMaxSize(),
				)
			}
		}

		Row(
			modifier = Modifier
				.align(Alignment.BottomStart)
				.padding(horizontal = 24.dp, vertical = 14.dp),
			horizontalArrangement = Arrangement.spacedBy(9.6.dp),
			verticalAlignment = Alignment.Bottom,
		) {
			AlbumArt(
				bitmap = artwork,
				progress = progress,
				modifier = Modifier.size(52.8.dp),
			)

			Column(modifier = Modifier.padding(bottom = 2.dp)) {
				Text(
					text = metadata.title ?: stringResource(R.string.unknown_title),
					style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.8.sp, lineHeight = 35.2.sp),
					color = Color.White,
				)
				metadata.artist?.let { artist ->
					Text(
						text = artist,
						style = MaterialTheme.typography.titleMedium,
						color = Color.White.copy(alpha = 0.7f),
					)
				}
			}
		}
	}
}

@Composable
private fun AlbumArt(
	bitmap: Bitmap?,
	progress: Float,
	modifier: Modifier = Modifier,
) {
	val animatedProgress by animateFloatAsState(
		targetValue = progress,
		animationSpec = tween(durationMillis = 250, easing = LinearEasing),
		label = "artProgress",
	)

	Box(modifier = modifier, contentAlignment = Alignment.Center) {
		Canvas(modifier = Modifier.fillMaxSize()) {
			val stroke = 2.dp.toPx()
			val inset = stroke / 2
			val corner = 8.dp.toPx()
			val rect = RoundRect(
				left = inset,
				top = inset,
				right = size.width - inset,
				bottom = size.height - inset,
				topLeftCornerRadius = CornerRadius(corner, corner),
				topRightCornerRadius = CornerRadius(corner, corner),
				bottomRightCornerRadius = CornerRadius(corner, corner),
				bottomLeftCornerRadius = CornerRadius(corner, corner),
			)

			// Track around the exact edge of the artwork.
			drawRoundRect(
				color = Color.White.copy(alpha = 0.18f),
				topLeft = Offset(inset, inset),
				size = Size(size.width - stroke, size.height - stroke),
				cornerRadius = CornerRadius(corner, corner),
				style = Stroke(width = stroke),
			)

			// Progress segment along the same rounded rectangle.
			val path = Path().apply { addRoundRect(rect) }
			val measure = PathMeasure()
			measure.setPath(path, false)
			val progressPath = Path()
			measure.getSegment(0f, measure.length * animatedProgress, progressPath, true)
			drawPath(
				path = progressPath,
				color = Color.White,
				style = Stroke(width = stroke, cap = StrokeCap.Round),
			)
		}

		Box(
			modifier = Modifier
				.matchParentSize()
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

/** Karaoke view: the current line is highlighted and the list scrolls to keep it centred. */
@Composable
private fun ScrollingLyrics(
	lines: List<LrcLine>,
	positionMs: Long,
	modifier: Modifier = Modifier,
) {
	val listState = rememberLazyListState()
	val lineHeight = 64.dp
	val currentIndex = remember(lines, positionMs) {
		lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
	}
	val centerOffset = with(LocalDensity.current) { -(185.dp).roundToPx() }

	LaunchedEffect(currentIndex) {
		listState.animateScrollToItem(currentIndex, centerOffset)
	}

	LazyColumn(
		modifier = modifier,
		state = listState,
		contentPadding = PaddingValues(vertical = 185.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		itemsIndexed(lines) { index, line ->
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
			lyrics = null,
			positionMs = 0L,
			modifier = Modifier.fillMaxSize(),
		)
	}
}

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun MusicScreenPlayingPreview() {
	val metadata = MusicMetadata(
		packageName = "com.example.music",
		title = "Song Title",
		artist = "Artist Name",
		album = "Album Name",
	)
	MusicFunTheme {
		MusicScreenContent(
			metadata = metadata,
			lyrics = LyricsUi(
				trackKey = metadata.trackKey,
				state = LyricsUiState.Synced(
					lines = listOf(
						LrcLine(0L, "First line of the song"),
						LrcLine(5_000L, "Second line of the song"),
						LrcLine(10_000L, "Third line of the song"),
						LrcLine(15_000L, "Fourth line of the song"),
						LrcLine(20_000L, "Fifth line of the song"),
					)
				),
			),
			positionMs = 6_000L,
			modifier = Modifier.fillMaxSize(),
		)
	}
}
