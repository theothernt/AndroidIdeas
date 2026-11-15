package com.neilturner.musicfun.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.abs
import kotlin.math.roundToInt

private const val FadeMs = 240

/** Highlight each line this much before its timestamp, to cover the audio output latency. */
private const val LyricsLeadMs = 1_000L

/** A gap this long between lyric timestamps is an instrumental break, not a pause between lines. */
private const val LongBreakMs = 8_000L

/** Let a line sit for a moment before the break note replaces it. */
private const val BreakNoteDelayMs = 4_000L

/** One swing of a break note, in milliseconds. */
private const val NoteSwingMs = 450

/** The second note starts this fraction into its swing, so the pair never mirrors exactly. */
private const val NoteStagger = 0.25f
private val ArtCorner = 5.dp
private val ArtSize = 95.dp

/** One lyric slot. The synced panel is exactly three of these tall: previous, current, next. */
private val LyricLineHeight = 96.dp

/** How strong the corner cover is, and how far it is scaled down before being smoothed back up. */
private const val BackdropAlpha = 0.5f
private const val BackdropDownscale = 60

/** The corner cover is this fraction of the screen's width across (75 % of a 16:9 screen's area). */
private const val BackdropScreenFraction = 0.75f

/** Working size of the cover before it is blurred and shrunk into the corner glow. */
private const val BackdropBlurSize = 256

/** Box-blur radius at [BackdropBlurSize]; three passes approximate a Gaussian. */
private const val BackdropBlurRadius = 32

@Composable
fun MusicScreen(
	modifier: Modifier = Modifier,
	onRequestNotificationAccess: () -> Unit = {},
) {
	val viewModel: MusicViewModel = viewModel()
	val metadata by viewModel.nowPlaying.collectAsStateWithLifecycle()
	val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
	val release by viewModel.release.collectAsStateWithLifecycle()
	val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
	val hasNotificationAccess by viewModel.hasNotificationAccess.collectAsStateWithLifecycle()

	// Access can only be granted in system settings, so re-read it when we come back.
	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
		viewModel.refreshNotificationAccess()
	}

	MusicScreenContent(
		metadata = metadata,
		lyrics = lyrics,
		releaseText = release?.takeIf { it.trackKey == metadata?.trackKey }?.text,
		positionMs = positionMs,
		hasNotificationAccess = hasNotificationAccess,
		onRequestNotificationAccess = onRequestNotificationAccess,
		modifier = modifier,
	)
}

@Composable
private fun MusicScreenContent(
	metadata: MusicMetadata?,
	lyrics: LyricsUi?,
	releaseText: String? = null,
	positionMs: Long,
	hasNotificationAccess: Boolean? = null,
	onRequestNotificationAccess: () -> Unit = {},
	modifier: Modifier = Modifier,
) {
	val showPermissionRequest = hasNotificationAccess == false
	val currentArtwork = metadata?.artwork ?: rememberUriBitmap(metadata?.artworkUri)

	// Keep the last cover while the info fades out, so stopping doesn't snap the aurora back to
	// the brand palette mid-fade.
	var retainedArtwork by remember { mutableStateOf<Bitmap?>(null) }
	LaunchedEffect(currentArtwork) {
		if (currentArtwork != null) retainedArtwork = currentArtwork
	}
	val auroraColors = rememberAuroraColors(if (metadata != null) currentArtwork else retainedArtwork)

	// Keep the cover up while the info fades out, but never carry it over into another track.
	var backdropArtwork by remember { mutableStateOf<Bitmap?>(null) }
	LaunchedEffect(metadata?.trackKey, currentArtwork) {
		when {
			currentArtwork != null -> backdropArtwork = currentArtwork
			metadata != null -> backdropArtwork = null
		}
	}

	Box(modifier = modifier.fillMaxSize()) {
		AuroraBackground(
			colors = auroraColors,
			modifier = Modifier.matchParentSize(),
		)

		// The cover, blurred into a soft glow in the top-right corner: a hint of the artwork
		// underneath the scrim, without competing with the drifting field above it.
		AnimatedVisibility(
			visible = metadata != null,
			enter = fadeIn(tween(durationMillis = FadeMs, easing = LinearEasing)),
			exit = fadeOut(tween(durationMillis = FadeMs, easing = LinearEasing)),
			modifier = Modifier.matchParentSize(),
			label = "albumArtBackdrop",
		) {
			AlbumArtBackdrop(
				artwork = backdropArtwork,
				modifier = Modifier.matchParentSize(),
			)
		}

		// Vignette and scrim, pre-rendered into a single bitmap: as live Compose gradients they cost
		// two full-screen shader passes per frame, which on this TV is what held the screen at 30 fps.
		ScrimOverlay(modifier = Modifier.matchParentSize())

		// One fade for the whole corner, and track -> track.
		Crossfade(
			targetState = metadata,
			modifier = Modifier.matchParentSize(),
			animationSpec = tween(durationMillis = FadeMs, easing = LinearEasing),
			label = "nowPlaying",
		) { track ->
			Box(modifier = Modifier.fillMaxSize()) {
				if (track != null) {
					NowPlayingLayer(
						metadata = track,
						lyrics = lyrics?.takeIf { it.trackKey == track.trackKey }?.state
							?: LyricsUiState.None,
						releaseText = releaseText,
						positionMs = positionMs,
					)
				}
			}
		}

		AnimatedVisibility(
			visible = showPermissionRequest,
			enter = fadeIn(tween(durationMillis = FadeMs, easing = LinearEasing)),
			exit = fadeOut(tween(durationMillis = FadeMs, easing = LinearEasing)),
			modifier = Modifier.align(Alignment.BottomStart),
			label = "permissionRequest",
		) {
			NotificationAccessButton(
				onClick = onRequestNotificationAccess,
				modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
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
 * The current cover as a soft glow in the top-right corner. The art is rasterised into a
 * [BackdropDownscale]-times smaller bitmap, masked by a radial fade so its square edges never show,
 * and scaled back up by [Image]; that is one cheap draw off the main thread instead of a per-frame
 * render-effect blur, which this TV cannot afford on top of the aurora.
 */
@Composable
private fun AlbumArtBackdrop(artwork: Bitmap?, modifier: Modifier = Modifier) {
	BoxWithConstraints(modifier = modifier) {
		val widthPx = constraints.maxWidth
		val heightPx = constraints.maxHeight

		// Hold the previous wash while a new one is rendered, so tracks don't blink.
		var wash by remember { mutableStateOf<ImageBitmap?>(null) }
		LaunchedEffect(artwork, widthPx, heightPx) {
			if (artwork != null && widthPx > 0 && heightPx > 0) {
				wash = withContext(Dispatchers.Default) { renderBackdrop(artwork, widthPx, heightPx) }
			}
		}

		wash?.let { image ->
			Image(
				bitmap = image,
				contentDescription = null,
				contentScale = ContentScale.FillBounds,
				alpha = BackdropAlpha,
				modifier = Modifier.fillMaxSize(),
			)
		}
	}
}

/** Repeatedly halves the cover down to [target] pixels so the result is smooth, not blocky. */
private fun scaleForBlur(source: Bitmap, target: Int): Bitmap {
	var current = source
	while (current.width / 2 >= target && current.height / 2 >= target) {
		current = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
	}
	return if (current.width == target) {
		current
	} else {
		Bitmap.createScaledBitmap(current, target, target, true)
	}
}

private fun renderBackdrop(artwork: Bitmap, widthPx: Int, heightPx: Int): ImageBitmap {
	val width = (widthPx / BackdropDownscale).coerceAtLeast(8)
	val height = (heightPx / BackdropDownscale).coerceAtLeast(8)
	val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
	val canvas = android.graphics.Canvas(bitmap)

	// The cover sits whole in the top-right at three quarters of the screen's width, then a radial
	// mask fades it out from its middle so the square's edges never show, only a soft glow.
	val side = (width * BackdropScreenFraction).coerceAtLeast(4f)
	val left = width - side
	val top = 0f
	val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
	// A real Gaussian-ish blur at 256 px: shrinking the cover alone left the outlines of shapes.
	canvas.drawBitmap(
		stackBlur(scaleForBlur(artwork, BackdropBlurSize), BackdropBlurRadius),
		null,
		RectF(left, top, left + side, top + side),
		Paint(Paint.FILTER_BITMAP_FLAG),
	)
	val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		shader = RadialGradient(
			left + side / 2f,
			top + side / 2f,
			side * 0.5f,
			intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00FFFFFF),
			floatArrayOf(0f, 0.4f, 1f),
			Shader.TileMode.CLAMP,
		)
		xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
	}
	canvas.drawRect(left, top, left + side, top + side, mask)
	canvas.restoreToCount(layer)

	return bitmap.asImageBitmap()
}

/** Three box-blur passes on packed ARGB pixels, which is close enough to a Gaussian. */
private fun stackBlur(source: Bitmap, radius: Int): Bitmap {
	if (radius <= 0) return source
	val width = source.width
	val height = source.height
	val pixels = IntArray(width * height)
	source.getPixels(pixels, 0, width, 0, 0, width, height)
	val temp = IntArray(pixels.size)
	repeat(3) {
		boxBlur(pixels, temp, width, height, radius, horizontal = true)
		boxBlur(temp, pixels, width, height, radius, horizontal = false)
	}
	return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
		setPixels(pixels, 0, width, 0, 0, width, height)
	}
}

private fun boxBlur(
	source: IntArray,
	target: IntArray,
	width: Int,
	height: Int,
	radius: Int,
	horizontal: Boolean,
) {
	val div = radius * 2 + 1
	val outer = if (horizontal) height else width
	val inner = if (horizontal) width else height
	val step = if (horizontal) 1 else width
	val line = if (horizontal) width else 1
	for (o in 0 until outer) {
		val base = o * line
		var a = 0
		var r = 0
		var g = 0
		var b = 0
		for (i in -radius..radius) {
			val v = source[base + i.coerceIn(0, inner - 1) * step]
			a += (v ushr 24) and 0xFF
			r += (v ushr 16) and 0xFF
			g += (v ushr 8) and 0xFF
			b += v and 0xFF
		}
		for (i in 0 until inner) {
			target[base + i * step] =
				((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
			val leaving = source[base + (i - radius).coerceIn(0, inner - 1) * step]
			val entering = source[base + (i + radius + 1).coerceIn(0, inner - 1) * step]
			a += ((entering ushr 24) and 0xFF) - ((leaving ushr 24) and 0xFF)
			r += ((entering ushr 16) and 0xFF) - ((leaving ushr 16) and 0xFF)
			g += ((entering ushr 8) and 0xFF) - ((leaving ushr 8) and 0xFF)
			b += (entering and 0xFF) - (leaving and 0xFF)
		}
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

	// A soft pool of shade over the lyric column: a bright cover wash would otherwise swallow
	// the dimmed previous and next lines while the current one stays legible.
	val lyrics = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		shader = RadialGradient(
			width * 0.70f,
			height * 0.5f,
			width * 0.33f,
			intArrayOf(
				Color.Black.copy(alpha = 0.30f).toArgb(),
				Color.Black.copy(alpha = 0.15f).toArgb(),
				Color.Transparent.toArgb(),
			),
			floatArrayOf(0f, 0.55f, 1f),
			Shader.TileMode.CLAMP,
		)
	}
	canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), lyrics)

	return bitmap.asImageBitmap()
}

@Composable
private fun NowPlayingLayer(
	metadata: MusicMetadata,
	lyrics: LyricsUiState,
	releaseText: String? = null,
	positionMs: Long,
) {
	val uriBitmap = rememberUriBitmap(metadata.artworkUri)
	val artwork = metadata.artwork ?: uriBitmap
	val lyricsPositionMs = positionMs + LyricsLeadMs

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
					positionMs = lyricsPositionMs,
					modifier = Modifier.fillMaxSize(),
				)
			}
		}

		Row(
			modifier = Modifier
				.align(Alignment.BottomStart)
				.padding(horizontal = 24.dp, vertical = 14.dp),
			horizontalArrangement = Arrangement.spacedBy(14.4.dp),
			verticalAlignment = Alignment.Bottom,
		) {
			AlbumArt(
				bitmap = artwork,
				modifier = Modifier.size(ArtSize),
			)

			Column(modifier = Modifier.padding(bottom = 2.dp)) {
				releaseText?.let { released ->
					Text(
						text = released,
						style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
						color = Color.White.copy(alpha = 0.6f),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
				Text(
					text = metadata.title ?: stringResource(R.string.unknown_title),
					style = MaterialTheme.typography.displaySmall.copy(fontSize = 25.9.sp, lineHeight = 31.7.sp),
					color = Color.White,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				// Artist, then the album or single it came from: "Genesis - Invisible Touch".
				val subtitle = listOfNotNull(metadata.artist, metadata.album)
					.filter { it.isNotBlank() }
					.joinToString(" - ")
				if (subtitle.isNotEmpty()) {
					Text(
						text = subtitle,
						style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.4.sp, lineHeight = 21.6.sp),
						color = Color.White.copy(alpha = 0.7f),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}
		}
	}
}

@Composable
private fun AlbumArt(
	bitmap: Bitmap?,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
			.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(ArtCorner))
			.clip(RoundedCornerShape(ArtCorner))
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

		// Nothing to show when LRCLIB has no lyrics for the track.
		LyricsUiState.None -> Unit
	}
}

/**
 * Karaoke view: the current line sits in the middle in white, the previous line above it and the
 * next one below, all at the same size. Every line change glides instead of jumping.
 */
@Composable
private fun ScrollingLyrics(
	lines: List<LrcLine>,
	positionMs: Long,
	modifier: Modifier = Modifier,
) {
	val listState = rememberLazyListState()
	val exactIndex = remember(lines, positionMs) { lines.indexOfLast { it.timeMs <= positionMs } }
	val currentIndex = exactIndex.coerceAtLeast(0)
	val showBreak = remember(lines, positionMs) { inLongBreak(lines, exactIndex, positionMs) }
	// Single-line changes glide; a new track, a seek or a restart snap straight to the current
	// line. Animating across a long jump left the panel scrolling the wrong lines for seconds
	// after the music had already moved.
	var positioned by remember(lines) { mutableStateOf(false) }
	var shownIndex by remember(lines) { mutableStateOf(currentIndex) }
	LaunchedEffect(currentIndex, lines) {
		if (!positioned || abs(currentIndex - shownIndex) > 1) {
			listState.scrollToItem(currentIndex)
		} else {
			listState.animateScrollToItem(currentIndex)
		}
		shownIndex = currentIndex
		positioned = true
	}

	Box(modifier = modifier, contentAlignment = Alignment.Center) {
		LazyColumn(
			modifier = Modifier
				.fillMaxWidth()
				.height(LyricLineHeight * 3),
			state = listState,
			contentPadding = PaddingValues(vertical = LyricLineHeight),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			itemsIndexed(lines) { index, line ->
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(LyricLineHeight),
					contentAlignment = Alignment.Center,
				) {
					if (showBreak && index == currentIndex) {
						RockingNote()
					} else {
						Text(
							text = line.text,
							style = MaterialTheme.typography.headlineSmall,
							color = if (index == currentIndex) {
								Color.White
							} else {
								Color.White.copy(alpha = 0.35f)
							},
							textAlign = TextAlign.Center,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
			}
		}
	}
}

/**
 * True while waiting for the next line across a long gap: a long intro, or an instrumental break
 * once the last line has had time to be read. The LRC has no break markers, so the gap between
 * timestamps is the evidence.
 */
private fun inLongBreak(lines: List<LrcLine>, index: Int, positionMs: Long): Boolean {
	val next = lines.getOrNull(index + 1) ?: return false
	val gapStart = lines.getOrNull(index)?.timeMs ?: 0L
	return next.timeMs - gapStart >= LongBreakMs &&
		(index < 0 || positionMs - gapStart >= BreakNoteDelayMs)
}

/**
 * Two music notes rocking in opposite directions while a long break waits for the next line. They
 * turn towards and away from each other, which reads as a quick dance rather than a tick, and the
 * second one starts a quarter of a swing later so the pair is never a perfect mirror.
 */
@Composable
private fun RockingNote(modifier: Modifier = Modifier) {
	val transition = rememberInfiniteTransition(label = "lyricBreak")
	val first = transition.animateFloat(
		initialValue = -15f,
		targetValue = 15f,
		animationSpec = noteSwing(phase = 0f),
		label = "lyricBreakFirst",
	)
	val second = transition.animateFloat(
		initialValue = -15f,
		targetValue = 15f,
		animationSpec = noteSwing(phase = NoteStagger),
		label = "lyricBreakSecond",
	)

	Row(
		modifier = modifier,
		horizontalArrangement = Arrangement.spacedBy(18.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		listOf(first, second).forEachIndexed { index, rock ->
			Text(
				text = "\u266A",
				style = MaterialTheme.typography.headlineMedium,
				color = Color.White.copy(alpha = 0.7f),
				modifier = Modifier.graphicsLayer {
					rotationZ = rock.value * if (index == 0) 1f else -1f
				},
			)
		}
	}
}

/** One note's swing; [phase] skips that fraction into the first swing before it starts. */
private fun noteSwing(phase: Float): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
	// Eased at both ends so the notes slow into each turn like a swinging pendulum.
	animation = tween(durationMillis = NoteSwingMs, easing = FastOutSlowInEasing),
	repeatMode = RepeatMode.Reverse,
	initialStartOffset = StartOffset(
		offsetMillis = (NoteSwingMs * phase).roundToInt(),
		offsetType = StartOffsetType.FastForward,
	),
)

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
