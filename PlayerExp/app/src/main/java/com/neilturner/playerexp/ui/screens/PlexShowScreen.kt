@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.neilturner.playerexp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.neilturner.playerexp.R
import com.neilturner.playerexp.data.plex.PlexShow
import com.neilturner.playerexp.data.plex.PlexShowEpisode
import com.neilturner.playerexp.data.plex.PlexShowPerson
import com.neilturner.playerexp.data.plex.PlexShowSeason
import com.neilturner.playerexp.data.plex.displayTitle
import com.neilturner.playerexp.data.plex.ratingLabel
import com.neilturner.playerexp.ui.viewmodels.PlexShowImageSizes
import com.neilturner.playerexp.ui.viewmodels.PlexShowUiState
import com.neilturner.playerexp.ui.viewmodels.PlexShowViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * A season page, laid out the way Plex lays its own out: the show's backdrop behind everything, a
 * row of season chips across the top, the season's episodes as a carousel of landscape stills, and
 * the focused episode's own details above them.
 *
 * Everything on screen follows the focus rather than the season alone. Moving along the carousel
 * re-reads the hero block with the episode now under focus, which is what makes this a page to
 * browse rather than a list to scroll.
 */
@Composable
fun PlexShowScreen(
    showRatingKey: String,
    showTitle: String? = null,
    modifier: Modifier = Modifier,
    onNavigateToPlayer: (ratingKey: String, title: String) -> Unit,
    viewModel: PlexShowViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sizes = rememberShowImageSizes()

    LaunchedEffect(showRatingKey, sizes) {
        viewModel.loadShow(showRatingKey, sizes)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            is PlexShowUiState.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)

            is PlexShowUiState.NotAuthorised -> StatusMessage(text = stringResource(R.string.plex_on_deck_not_authorised))

            is PlexShowUiState.Error -> StatusMessage(text = current.message)

            is PlexShowUiState.Success -> ShowPage(
                show = current.show,
                showTitle = showTitle,
                sizes = sizes,
                onNavigateToPlayer = onNavigateToPlayer,
                onSetWatched = { ratingKey, watched -> viewModel.setWatched(ratingKey, watched) }
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/**
 * The pixel sizes every image on this screen is fetched at, so Plex serves each one at roughly the
 * size it is drawn rather than sending the full-size scan.
 */
@Composable
fun rememberShowImageSizes(): PlexShowImageSizes {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp, density) {
        fun pixels(dp: Dp): Int = with(density) { dp.roundToPx() }

        val stillWidth = (configuration.screenWidthDp * STILL_WIDTH_FRACTION).dp
        // 16:9 landscape, so the height is the width times the ratio, not divided by it.
        val stillHeight = stillWidth * STILL_ASPECT_RATIO
        val avatar = (configuration.screenHeightDp * AVATAR_HEIGHT_FRACTION).dp
        val art = configuration.screenWidthDp.dp

        PlexShowImageSizes(
            poster = IntSize(pixels((configuration.screenHeightDp * 0.48f).dp), pixels((configuration.screenHeightDp * 0.32f).dp)),
            art = IntSize(pixels(art), pixels(configuration.screenHeightDp.dp)),
            still = IntSize(pixels(stillWidth), pixels(stillHeight)),
            avatar = IntSize(pixels(avatar), pixels(avatar))
        )
    }
}

@Composable
private fun ShowPage(
    show: PlexShow,
    showTitle: String?,
    sizes: PlexShowImageSizes,
    onNavigateToPlayer: (ratingKey: String, title: String) -> Unit,
    onSetWatched: (ratingKey: String, watched: Boolean) -> Unit
) {
    if (show.seasons.isEmpty()) {
        ShowBackdrop(show = show, sizes = sizes)
        Centered { StatusMessage(text = stringResource(R.string.plex_show_no_episodes)) }
        return
    }

    var seasonIndex by remember { mutableIntStateOf(0) }
    var episodeIndex by remember { mutableIntStateOf(0) }

    val safeSeasonIndex = seasonIndex.coerceIn(0, show.seasons.lastIndex)
    val season = show.seasons[safeSeasonIndex]
    val episodes = season.episodes
    val safeEpisodeIndex = episodeIndex.coerceIn(0, (episodes.lastIndex).coerceAtLeast(0))
    val focusedEpisode = episodes.getOrNull(safeEpisodeIndex)

    // A different season means a different set of episodes, so the focus goes back to its first.
    LaunchedEffect(safeSeasonIndex) { episodeIndex = 0 }

    Box(modifier = Modifier.fillMaxSize()) {
        ShowBackdrop(show = show, sizes = sizes)

        Column(modifier = Modifier.fillMaxSize()) {
            SeasonChips(
                seasons = show.seasons,
                selectedIndex = safeSeasonIndex,
                onSelect = { seasonIndex = it }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = SCREEN_HORIZONTAL_PADDING),
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(HERO_TOP_GAP))

                ShowHero(
                    show = show,
                    season = season,
                    episode = focusedEpisode,
                    episodePosition = if (episodes.isEmpty()) null else safeEpisodeIndex + 1,
                    episodeCount = episodes.size,
                    showTitle = showTitle
                )

                Spacer(modifier = Modifier.height(HERO_TO_CAROUSEL_GAP))

                EpisodeCarousel(
                    episodes = episodes,
                    focusedIndex = safeEpisodeIndex,
                    stillSize = sizes.still,
                    onFocused = { episodeIndex = it },
                    onPlay = { episode ->
                        onNavigateToPlayer(episode.ratingKey, episode.displayTitle(showTitle))
                    }
                )

                Spacer(modifier = Modifier.height(ACTION_ROW_TOP_GAP))

                EpisodeActions(
                    episode = focusedEpisode,
                    onPlay = { episode ->
                        onNavigateToPlayer(episode.ratingKey, episode.displayTitle(showTitle))
                    },
                    onToggleWatched = { episode -> onSetWatched(episode.ratingKey, !episode.isWatched) }
                )

                Spacer(modifier = Modifier.height(CAST_SECTION_TOP_GAP))

                CastAndCrew(episode = focusedEpisode)
            }
        }
    }
}

/**
 * The show's own art, dimmed under a gradient so white text stays readable over whatever the
 * artwork happens to be. A show with no art falls back to its poster rather than showing nothing.
 */
@Composable
private fun ShowBackdrop(show: PlexShow, sizes: PlexShowImageSizes) {
    Box(modifier = Modifier.fillMaxSize()) {
        val artUrl = show.artUrl ?: show.posterUrl
        if (artUrl != null) {
            AsyncImage(
                model = rememberPosterRequest(artUrl, sizes.art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Darker at the top and bottom than in the middle, so the chips and the carousel both sit
        // on something that will hold their edges.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.72f),
                        0.35f to Color.Black.copy(alpha = 0.45f),
                        0.7f to Color.Black.copy(alpha = 0.78f),
                        1f to Color.Black.copy(alpha = 0.94f)
                    )
                )
        )
    }
}

/** "Show | Season 1 | Season 2", one chip per season, the selected one picked out. */
@Composable
private fun SeasonChips(
    seasons: List<PlexShowSeason>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SCREEN_HORIZONTAL_PADDING, vertical = CHIP_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        itemsIndexed(seasons, key = { index, season -> "season-${season.seasonNumber}" }) { index, season ->
            SeasonChip(
                label = season.title,
                selected = index == selectedIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier,
        shape = ButtonDefaults.shape(shape = CHIP_SHAPE),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) CHIP_SELECTED_COLOR else CHIP_COLOR
        ),
        scale = ButtonDefaults.scale(focusedScale = CHIP_FOCUSED_SCALE, pressedScale = PRESSED_CHIP_SCALE),
        border = ButtonDefaults.border(
            focusedBorder = Border(border = BorderStroke(CHIP_FOCUS_BORDER_WIDTH, Color.White), shape = CHIP_SHAPE),
            border = Border(border = BorderStroke(CHIP_RESTING_BORDER_WIDTH, Color.White.copy(alpha = 0.18f)), shape = CHIP_SHAPE)
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = Color.White,
            maxLines = 1
        )
    }
}

/**
 * The show's title and, underneath it, whatever episode the carousel is on. Plex puts the show's
 * logo here and the episode's plot under the ratings; only the plot moves as the focus moves.
 */
@Composable
private fun ShowHero(
    show: PlexShow,
    season: PlexShowSeason,
    episode: PlexShowEpisode?,
    episodePosition: Int?,
    episodeCount: Int,
    showTitle: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = show.title ?: showTitle.orEmpty(),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!season.displayTitle.isNullOrBlank() && season.displayTitle != season.title) {
            Text(
                text = season.displayTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        EpisodeMetaLine(
            episode = episode,
            episodePosition = episodePosition,
            episodeCount = episodeCount,
            contentRating = show.contentRating
        )

        EpisodeRatingRow(episode = episode)

        if (!episode?.summary.isNullOrBlank()) {
            Text(
                text = episode.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = EPISODE_SUMMARY_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }

        val directors = episode?.directors?.map { it.name }.orEmpty()
        if (directors.isNotEmpty()) {
            Text(
                text = stringResource(R.string.plex_show_directed_by, directors.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** "S2 • E1   Aug 27, 2026   53m", plus the season count when the show has more than one. */
@Composable
private fun EpisodeMetaLine(
    episode: PlexShowEpisode?,
    episodePosition: Int?,
    episodeCount: Int,
    contentRating: String?
) {
    val parts = buildList {
        episode?.let {
            val season = it.seasonNumber
            val number = it.episodeNumber
            when {
                season != null && number != null -> add("S$season • E$number")
                number != null -> add("E$number")
            }
        }
        episode?.airDate?.let { add(formatAirDate(it)) }
        episode?.duration?.takeIf { it > 0 }?.let { add(formatRuntime(it)) }
        episode?.let { if (it.isWatched) add(stringResource(R.string.plex_show_watched_short)) }
        if (episodePosition != null && episodeCount > 0) add("$episodePosition/$episodeCount")
        contentRating?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    if (parts.isEmpty()) return

    Row(
        modifier = Modifier.padding(vertical = META_LINE_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(META_LINE_GAP)
    ) {
        parts.forEach { part ->
            Text(
                text = part,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

/** "IMDb 7.5  TMDB 7.9", or nothing at all when the episode carries no rating. */
@Composable
private fun EpisodeRatingRow(episode: PlexShowEpisode?) {
    val episodeRating = episode?.rating
    val label = episode?.ratingLabel()
    if (episodeRating == null || label == null) return

    Row(
        modifier = Modifier.padding(bottom = META_LINE_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(RATING_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = formatRating(episodeRating),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * The season's episodes as landscape stills. The focused card takes focus itself, so D-pad left and
 * right move along the row and [onFocused] keeps the hero block in step with it.
 */
@Composable
private fun EpisodeCarousel(
    episodes: List<PlexShowEpisode>,
    focusedIndex: Int,
    stillSize: IntSize,
    onFocused: (Int) -> Unit,
    onPlay: (PlexShowEpisode) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var focusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(episodes.firstOrNull()?.ratingKey) {
        if (episodes.isNotEmpty() && !focusRequested) {
            focusRequested = true
            focusRequester.requestFocus()
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ROW_ITEM_SPACING),
        // The row starts in from the left edge, under the title, but runs off the right one: the
        // next still should be half on screen so the row reads as continuing, the way Plex's own
        // season carousel does.
        contentPadding = PaddingValues(
            start = SCREEN_HORIZONTAL_PADDING,
            end = 0.dp,
            top = FOCUSED_POSTER_OVERHANG,
            bottom = POSTER_ROW_BOTTOM_PADDING
        )
    ) {
        itemsIndexed(episodes, key = { _, episode -> episode.ratingKey }) { index, episode ->
            EpisodeStillCard(
                episode = episode,
                size = stillSize,
                // The requester has to sit on a single card, the first, so the row is usable the
                // moment it appears. It is never asked for again: moving between seasons replaces
                // the list, and re-claiming focus on every arrival would drag the user back to
                // episode one mid-browse.
                modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                onFocused = { onFocused(index) },
                onPlay = { onPlay(episode) }
            )
        }
    }
}

@Composable
private fun EpisodeStillCard(
    episode: PlexShowEpisode,
    size: IntSize,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onPlay: () -> Unit
) {
    val density = LocalDensity.current
    val widthDp = with(density) { size.width.toDp() }
    val heightDp = with(density) { size.height.toDp() }

    Card(
        onClick = onPlay,
        modifier = modifier
            .size(width = widthDp, height = heightDp)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = CardDefaults.shape(shape = STILL_SHAPE),
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        scale = CardDefaults.scale(
            focusedScale = FOCUSED_POSTER_SCALE,
            pressedScale = PRESSED_POSTER_SCALE
        ),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(FOCUS_BORDER_WIDTH, CARD_BORDER_COLOR), shape = STILL_SHAPE)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            if (!episode.thumbUrl.isNullOrBlank()) {
                AsyncImage(
                    model = rememberPosterRequest(episode.thumbUrl, size),
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            // The episode number sits in the top corner; a watched episode ticks beside it, which
            // is the one mark on the card that says where the user got to.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(STILL_BADGE_PADDING)
                    .background(BADGE_COLOR, BADGE_SHAPE)
                    .padding(horizontal = BADGE_HORIZONTAL_PADDING, vertical = BADGE_VERTICAL_PADDING),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BADGE_INNER_GAP)
                ) {
                    if (episode.isWatched) {
                        Canvas(modifier = Modifier.size(BADGE_CHECK_SIZE)) {
                            val stroke = this.size.minDimension * 0.14f
                            drawPath(
                                path = Path().apply {
                                    moveTo(this@Canvas.size.width * 0.16f, this@Canvas.size.height * 0.52f)
                                    lineTo(this@Canvas.size.width * 0.42f, this@Canvas.size.height * 0.76f)
                                    lineTo(this@Canvas.size.width * 0.86f, this@Canvas.size.height * 0.24f)
                                },
                                color = Color.White,
                                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                        }
                    }
                    Text(
                        text = episode.episodeNumber?.let { "E$it" } ?: episode.seasonNumber?.let { "S$it" } ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (episode.hasProgress) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = POSTER_BAR_INSET, vertical = POSTER_BAR_INSET)
                ) {
                    ProgressOverlay(episode.progressFraction)
                }
            }
        }
    }
}

/**
 * The two controls that act on the focused episode. Plex shows a wider set here, but anything not
 * yet wired to the server is left off rather than drawn as a button that does nothing.
 */
@Composable
private fun EpisodeActions(
    episode: PlexShowEpisode?,
    onPlay: (PlexShowEpisode) -> Unit,
    onToggleWatched: (PlexShowEpisode) -> Unit
) {
    if (episode == null) return

    Row(
        modifier = Modifier.padding(start = SCREEN_HORIZONTAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(ACTION_BUTTON_GAP)
    ) {
        ActionButton(
            onClick = { onPlay(episode) },
            contentDescription = stringResource(R.string.plex_show_play)
        ) {
            Canvas(modifier = Modifier.size(ACTION_GLYPH_SIZE)) {
                val inset = this.size.width * 0.28f
                drawPath(
                    path = Path().apply {
                        moveTo(inset, this@Canvas.size.height * 0.18f)
                        lineTo(this@Canvas.size.width - inset, this@Canvas.size.height * 0.5f)
                        lineTo(inset, this@Canvas.size.height * 0.82f)
                        close()
                    },
                    color = Color.White
                )
            }
        }

        ActionButton(
            onClick = { onToggleWatched(episode) },
            contentDescription = stringResource(
                if (episode.isWatched) R.string.plex_show_mark_unwatched else R.string.plex_show_mark_watched
            )
        ) {
            WatchedGlyph(checked = episode.isWatched)
        }
    }
}

@Composable
private fun ActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(ACTION_BUTTON_SIZE),
        shape = ButtonDefaults.shape(shape = CircleShape),
        colors = ButtonDefaults.colors(containerColor = ACTION_BUTTON_COLOR),
        scale = ButtonDefaults.scale(focusedScale = ACTION_FOCUSED_SCALE, pressedScale = PRESSED_CHIP_SCALE),
        border = ButtonDefaults.border(focusedBorder = Border(border = BorderStroke(ACTION_FOCUS_BORDER_WIDTH, Color.White), shape = CircleShape))
    ) {
        Box(modifier = Modifier.size(ACTION_BUTTON_SIZE), contentAlignment = Alignment.Center) { content() }
    }
}

/** A ticked circle when the episode is watched, an empty one when it is not. */
@Composable
private fun WatchedGlyph(checked: Boolean) {
    Canvas(modifier = Modifier.size(ACTION_GLYPH_SIZE)) {
        val stroke = this.size.minDimension * 0.1f
        drawCircle(
            color = Color.White,
            radius = this.size.minDimension / 2f - stroke,
            style = Stroke(width = stroke)
        )
        if (checked) {
            drawPath(
                path = Path().apply {
                    moveTo(this@Canvas.size.width * 0.28f, this@Canvas.size.height * 0.52f)
                    lineTo(this@Canvas.size.width * 0.44f, this@Canvas.size.height * 0.68f)
                    lineTo(this@Canvas.size.width * 0.74f, this@Canvas.size.height * 0.34f)
                },
                color = Color.White,
                style = Stroke(width = stroke * 1.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

/** Whoever is credited on the focused episode, as the headshots Plex files them under. */
@Composable
private fun CastAndCrew(episode: PlexShowEpisode?) {
    val people = remember(episode?.ratingKey) { episode?.people.orEmpty() }
    if (people.isEmpty()) return

    Column {
        Text(
            text = stringResource(R.string.plex_show_cast_crew),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = SCREEN_HORIZONTAL_PADDING, bottom = CAST_ROW_TOP_GAP)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ROW_ITEM_SPACING),
            contentPadding = PaddingValues(
                start = SCREEN_HORIZONTAL_PADDING,
                end = SCREEN_HORIZONTAL_PADDING,
                top = FOCUSED_POSTER_OVERHANG,
                bottom = POSTER_ROW_BOTTOM_PADDING
            )
        ) {
            itemsIndexed(people, key = { _, person -> person.name }) { _, person ->
                CastAvatar(person = person)
            }
        }
    }
}

@Composable
private fun CastAvatar(person: PlexShowPerson) {
    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (person.imageUrl != null) {
            AsyncImage(
                model = rememberPosterRequest(person.imageUrl, AVATAR_PIXELS),
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = person.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

/** `2026-01-05` reads as "Jan 5, 2026"; anything Plex sends in another shape is left alone. */
private fun formatAirDate(raw: String): String = runCatching {
    LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}.getOrDefault(raw)

/** Plex reports runtime in milliseconds. */
private fun formatRuntime(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${totalMinutes}m"
    }
}

private fun formatRating(rating: Float): String =
    if (rating % 1f == 0f) rating.toInt().toString() else String.format("%.1f", rating)

private val STILL_ASPECT_RATIO = 9f / 16f
private const val STILL_WIDTH_FRACTION = 0.28f
private const val AVATAR_HEIGHT_FRACTION = 0.075f
private val AVATAR_SIZE = 84.dp
private val AVATAR_PIXELS = IntSize(252, 252)

private val STILL_SHAPE = RoundedCornerShape(10.dp)
private val CHIP_SHAPE = RoundedCornerShape(50)
private val BADGE_SHAPE = RoundedCornerShape(6.dp)
private val CHIP_COLOR = Color(0x33FFFFFF)
private val CHIP_SELECTED_COLOR = Color.White.copy(alpha = 0.22f)
private val CHIP_SPACING = 10.dp
private val CHIP_VERTICAL_PADDING = 16.dp
private const val CHIP_FOCUSED_SCALE = 1.08f
private val PRESSED_CHIP_SCALE = 0.94f
private val CHIP_FOCUS_BORDER_WIDTH = 2.dp
private val CHIP_RESTING_BORDER_WIDTH = 1.dp

private val HERO_TOP_GAP = 12.dp
private val HERO_TO_CAROUSEL_GAP = 8.dp
private const val EPISODE_SUMMARY_MAX_LINES = 3
private val META_LINE_VERTICAL_PADDING = 6.dp
private val META_LINE_GAP = 16.dp
private val RATING_GAP = 6.dp

private val ACTION_ROW_TOP_GAP = 4.dp
private val ACTION_BUTTON_SIZE = 52.dp
private val ACTION_GLYPH_SIZE = 24.dp
private val ACTION_BUTTON_GAP = 20.dp
private val ACTION_BUTTON_COLOR = Color.White.copy(alpha = 0.14f)
private const val ACTION_FOCUSED_SCALE = 1.1f
private val ACTION_FOCUS_BORDER_WIDTH = 2.dp

private val CAST_SECTION_TOP_GAP = 12.dp
private val CAST_ROW_TOP_GAP = 8.dp

private val STILL_BADGE_PADDING = 8.dp
private val BADGE_COLOR = Color.Black.copy(alpha = 0.6f)
private val BADGE_HORIZONTAL_PADDING = 8.dp
private val BADGE_VERTICAL_PADDING = 4.dp
private val BADGE_INNER_GAP = 4.dp
private val BADGE_CHECK_SIZE = 12.dp
